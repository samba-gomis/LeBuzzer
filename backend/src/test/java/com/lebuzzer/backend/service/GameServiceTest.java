package com.lebuzzer.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import com.lebuzzer.backend.dto.ErrorCode;
import com.lebuzzer.backend.dto.GameCreated;
import com.lebuzzer.backend.dto.GameSnapshot;
import com.lebuzzer.backend.dto.LeaderboardEntry;
import com.lebuzzer.backend.dto.PlayerSelf;
import com.lebuzzer.backend.dto.PlayerView;
import com.lebuzzer.backend.dto.RoomEvent;
import com.lebuzzer.backend.model.GameStatus;
import com.lebuzzer.backend.model.ParticipantRole;
import com.lebuzzer.backend.model.Question;
import com.lebuzzer.backend.model.Theme;

/**
 * Unit tests of the game rules, without Spring nor WebSocket: the messages are recorded by a fake
 * notifier and time is moved by hand with a fake clock.
 */
class GameServiceTest {

	private static final String HOST = "host-connection";
	private static final Instant START = Instant.parse("2026-01-01T10:00:00Z");

	/** Q1: right answer = 1. Q2: right answer = 0. 1000 points, 20 s each. */
	private static final Question Q1 = new Question("q1", "Question 1", List.of("A", "B", "C", "D"), 1, 1000, 20_000);
	private static final Question Q2 = new Question("q2", "Question 2", List.of("A", "B", "C", "D"), 0, 1000, 20_000);

	private MutableClock clock;
	private RecordingNotifier notifier;
	private ScheduledExecutorService timer;
	private GameService service;

	@BeforeEach
	void setUp() {
		clock = new MutableClock(START);
		notifier = new RecordingNotifier();
		timer = Executors.newSingleThreadScheduledExecutor();
		service = new GameService(new TestCatalog(), new RandomCodeGenerator(), notifier, clock, timer,
				new MessageMetrics());
	}

	@AfterEach
	void tearDown() {
		timer.shutdownNow();
	}

	// ------------------------------------------------------------------ game creation

	@Test
	void gameCodes_areUnique_evenWhenTheGeneratorRepeatsItself() {
		Iterator<String> codes = List.of("AAAAA", "AAAAA", "BBBBB").iterator();
		GameService repeatingService = new GameService(new TestCatalog(), codes::next, notifier, clock, timer,
				new MessageMetrics());

		repeatingService.createGame("c1", "quiz");
		repeatingService.createGame("c2", "quiz");

		assertEquals("AAAAA", notifier.lastPrivate("c1", GameCreated.class).code());
		assertEquals("BBBBB", notifier.lastPrivate("c2", GameCreated.class).code());
	}

	@Test
	void createGame_withAnUnknownTheme_isRefused() {
		assertGameError(ErrorCode.THEME_NOT_FOUND, () -> service.createGame(HOST, "nope"));
	}

	// ------------------------------------------------------------------ joining

	@Test
	void join_unknownGame_isRefused() {
		assertGameError(ErrorCode.GAME_NOT_FOUND, () -> service.join("ZZZZZ", "p1", "Samba", null));
	}

	@Test
	void join_isCaseInsensitiveOnTheCode() {
		GameCreated game = createGame();
		service.join(game.code().toLowerCase(), "p1", "Samba", null);
		assertEquals(game.code(), notifier.lastSnapshot("p1").code());
	}

	@Test
	void join_refusesADuplicateNickname_ignoringCaseAndSpaces() {
		GameCreated game = createGame();
		join(game.code(), "p1", "Samba");
		assertGameError(ErrorCode.NICKNAME_TAKEN, () -> service.join(game.code(), "p2", "  samba ", null));
	}

	@Test
	void join_refusesAnEmptyOrTooLongNickname() {
		GameCreated game = createGame();
		assertGameError(ErrorCode.INVALID_NICKNAME, () -> service.join(game.code(), "p1", "   ", null));
		assertGameError(ErrorCode.INVALID_NICKNAME, () -> service.join(game.code(), "p1", "a".repeat(21), null));
		assertEquals("a".repeat(20), join(game.code(), "p1", "a".repeat(20)).nickname());
	}

	@Test
	void theSecretPlayerId_isNeverBroadcast() {
		GameCreated game = createGame();
		PlayerSelf me = join(game.code(), "p1", "Samba");

		RoomEvent.PlayersUpdated update = notifier.lastRoomEvent(game.code(), RoomEvent.PlayersUpdated.class);
		assertEquals(me.publicId(), update.players().getFirst().publicId());
		assertNotEquals(me.playerId(), me.publicId());
		for (RecordingNotifier.RoomMessage message : notifier.roomMessages) {
			assertFalse(message.event().toString().contains(me.playerId()), "playerId leaked in " + message);
		}
	}

	@Test
	void aLateJoiner_receivesTheOpenQuestionWithItsDeadline() {
		GameCreated game = createGame();
		service.startQuestion(game.code(), game.hostToken());
		clock.advanceMillis(5_000);

		service.join(game.code(), "late", "Retardataire", null);

		GameSnapshot snapshot = notifier.lastSnapshot("late");
		assertEquals(GameStatus.QUESTION_OPEN, snapshot.status());
		assertEquals("q1", snapshot.currentQuestion().questionId());
		assertEquals(START.toEpochMilli() + 20_000, snapshot.currentQuestion().endsAt());
		assertEquals(START.toEpochMilli() + 5_000, snapshot.serverNow());
		assertNull(snapshot.correctIndex(), "the right answer must stay secret while the question is open");
		assertNotNull(snapshot.leaderboard());
	}

	@Test
	void aLateJoiner_receivesTheCurrentLeaderboard() {
		GameCreated game = createGame();
		join(game.code(), "p1", "Samba");
		service.startQuestion(game.code(), game.hostToken());
		service.submitAnswer(game.code(), "p1", "q1", 1);
		service.closeQuestion(game.code(), game.hostToken());
		service.startQuestion(game.code(), game.hostToken());

		service.join(game.code(), "late", "Retardataire", null);

		List<LeaderboardEntry> leaderboard = notifier.lastSnapshot("late").leaderboard();
		assertEquals(List.of("Samba", "Retardataire"), leaderboard.stream().map(LeaderboardEntry::nickname).toList());
		assertEquals(1000, leaderboard.getFirst().score());
	}

	@Test
	void aQuestionStart_carriesTheCurrentLeaderboard_withEveryPlayer() {
		GameCreated game = createGame();
		join(game.code(), "p1", "Samba");
		service.startQuestion(game.code(), game.hostToken());
		service.submitAnswer(game.code(), "p1", "q1", 1);
		service.closeQuestion(game.code(), game.hostToken());
		join(game.code(), "p2", "Lina");

		service.startQuestion(game.code(), game.hostToken());

		List<LeaderboardEntry> leaderboard = notifier.lastRoomEvent(game.code(), RoomEvent.QuestionStarted.class)
				.leaderboard();
		assertEquals(List.of("Samba", "Lina"), leaderboard.stream().map(LeaderboardEntry::nickname).toList());
		assertEquals(List.of(1000, 0), leaderboard.stream().map(LeaderboardEntry::score).toList());
	}

	@Test
	void theLeaderboard_revealsNothingAboutTheOpenQuestion() {
		GameCreated game = createGame();
		join(game.code(), "p1", "Samba");
		service.startQuestion(game.code(), game.hostToken());
		service.submitAnswer(game.code(), "p1", "q1", 1);

		service.join(game.code(), "late", "Retardataire", null);

		LeaderboardEntry samba = notifier.lastSnapshot("late").leaderboard().stream()
				.filter(entry -> entry.nickname().equals("Samba"))
				.findFirst()
				.orElseThrow();
		assertEquals(0, samba.score(), "points are only added when the question closes");
		assertEquals(0, samba.lastPoints());
	}

	// ------------------------------------------------------------------ disconnection and rejoin

	@Test
	void aRejoiningPlayer_getsBackScoreAndAnswer() {
		GameCreated game = createGame();
		PlayerSelf first = join(game.code(), "p1-old", "Samba");
		service.startQuestion(game.code(), game.hostToken());
		service.submitAnswer(game.code(), "p1-old", "q1", 1);
		service.closeQuestion(game.code(), game.hostToken());
		service.handleDisconnect("p1-old");

		service.join(game.code(), "p1-new", null, first.playerId());

		GameSnapshot snapshot = notifier.lastSnapshot("p1-new");
		assertEquals(1000, snapshot.you().score());
		assertEquals(1, snapshot.you().choiceIndex());
		assertEquals(first.publicId(), snapshot.you().publicId());
		assertTrue(playerView(game.code(), first.publicId()).connected());
	}

	@Test
	void aClosedTab_marksThePlayerDisconnected_andTheGameGoesOn() {
		GameCreated game = createGame();
		PlayerSelf leaving = join(game.code(), "p1", "Samba");
		join(game.code(), "p2", "Lina");

		service.handleDisconnect("p1");

		assertFalse(playerView(game.code(), leaving.publicId()).connected());
		service.startQuestion(game.code(), game.hostToken());
		service.submitAnswer(game.code(), "p2", "q1", 1);
		assertEquals(1, notifier.lastPrivate(HOST, RoomEvent.AnswerProgress.class).answeredCount());
	}

	@Test
	void theOldConnectionClosing_afterARejoin_doesNotDisconnectThePlayer() {
		GameCreated game = createGame();
		PlayerSelf me = join(game.code(), "old", "Samba");
		service.join(game.code(), "new", null, me.playerId());

		service.handleDisconnect("old");

		assertTrue(playerView(game.code(), me.publicId()).connected());
	}

	@Test
	void theHostLeaving_doesNotStopTheGame_andTheHostCanComeBack() {
		GameCreated game = createGame();
		service.handleDisconnect(HOST);

		service.hostJoin(game.code(), "host-again", game.hostToken());

		assertEquals(ParticipantRole.HOST, notifier.lastSnapshot("host-again").role());
		service.startQuestion(game.code(), game.hostToken());
	}

	// ------------------------------------------------------------------ answers

	@Test
	void onlyTheFirstAnswerCounts() {
		GameCreated game = createGame();
		join(game.code(), "p1", "Samba");
		service.startQuestion(game.code(), game.hostToken());

		service.submitAnswer(game.code(), "p1", "q1", 1);
		assertGameError(ErrorCode.ALREADY_ANSWERED, () -> service.submitAnswer(game.code(), "p1", "q1", 0));

		assertEquals(1, notifier.allPrivate(HOST, RoomEvent.AnswerProgress.class).size(),
				"a refused second answer must not be sent");
		service.closeQuestion(game.code(), game.hostToken());
		assertEquals(1000, leaderboardEntry(game.code(), "Samba").lastPoints());
	}

	@Test
	void theAnswerProgress_goesToTheHostOnly() {
		GameCreated game = createGame();
		join(game.code(), "p1", "Samba");
		service.startQuestion(game.code(), game.hostToken());

		service.submitAnswer(game.code(), "p1", "q1", 1);

		assertEquals(1, notifier.lastPrivate(HOST, RoomEvent.AnswerProgress.class).answeredCount());
		assertTrue(notifier.roomEvents(game.code(), RoomEvent.AnswerProgress.class).isEmpty(),
				"not broadcast to the room");
		assertTrue(notifier.allPrivate("p1", RoomEvent.AnswerProgress.class).isEmpty(), "players do not receive it");
	}

	@Test
	void anAnswerAfterTheDeadline_isRefused_evenBeforeTheTimerRuns() {
		GameCreated game = createGame();
		join(game.code(), "p1", "Samba");
		service.startQuestion(game.code(), game.hostToken());
		clock.advanceMillis(20_001);

		assertGameError(ErrorCode.QUESTION_CLOSED, () -> service.submitAnswer(game.code(), "p1", "q1", 1));
	}

	@Test
	void anAnswerToAClosedQuestion_isRefused() {
		GameCreated game = createGame();
		join(game.code(), "p1", "Samba");
		service.startQuestion(game.code(), game.hostToken());
		service.closeQuestion(game.code(), game.hostToken());

		assertGameError(ErrorCode.QUESTION_CLOSED, () -> service.submitAnswer(game.code(), "p1", "q1", 1));
	}

	@Test
	void anAnswerFromSomeoneWhoDidNotJoin_isRefused() {
		GameCreated game = createGame();
		service.startQuestion(game.code(), game.hostToken());

		assertGameError(ErrorCode.NOT_IN_GAME, () -> service.submitAnswer(game.code(), "stranger", "q1", 1));
	}

	@Test
	void theScore_dependsOnTheAnswerAndOnTheSpeed_andIsRevealedAtTheClose() {
		GameCreated game = createGame();
		join(game.code(), "fast", "Rapide");
		join(game.code(), "slow", "Lent");
		join(game.code(), "wrong", "Faux");
		service.startQuestion(game.code(), game.hostToken());

		service.submitAnswer(game.code(), "fast", "q1", 1); // right, 20 s left out of 20
		service.submitAnswer(game.code(), "wrong", "q1", 3); // wrong
		clock.advanceMillis(10_000);
		service.submitAnswer(game.code(), "slow", "q1", 1); // right, 10 s left out of 20

		service.sync(game.code(), "fast");
		assertEquals(0, notifier.lastSnapshot("fast").you().score(), "no score before the close");

		service.closeQuestion(game.code(), game.hostToken());
		assertEquals(1000, leaderboardEntry(game.code(), "Rapide").score());
		assertEquals(750, leaderboardEntry(game.code(), "Lent").score());
		assertEquals(0, leaderboardEntry(game.code(), "Faux").score());
		assertEquals(List.of(1, 2, 3), lastLeaderboard(game.code()).stream().map(LeaderboardEntry::rank).toList());
	}

	// ------------------------------------------------------------------ host rights

	@Test
	void hostActions_needTheRightHostToken() {
		GameCreated game = createGame();
		assertGameError(ErrorCode.INVALID_HOST_TOKEN, () -> service.startQuestion(game.code(), "wrong"));
		assertGameError(ErrorCode.INVALID_HOST_TOKEN, () -> service.startQuestion(game.code(), null));
		assertGameError(ErrorCode.INVALID_HOST_TOKEN, () -> service.closeQuestion(game.code(), "wrong"));
		assertGameError(ErrorCode.INVALID_HOST_TOKEN, () -> service.endGame(game.code(), "wrong"));
		assertGameError(ErrorCode.INVALID_HOST_TOKEN, () -> service.hostJoin(game.code(), "intruder", "wrong"));
	}

	@Test
	void startingAQuestion_afterTheLastOne_isRefused() {
		GameCreated game = createGame();
		service.startQuestion(game.code(), game.hostToken());
		service.closeQuestion(game.code(), game.hostToken());
		service.startQuestion(game.code(), game.hostToken());
		service.closeQuestion(game.code(), game.hostToken());

		assertGameError(ErrorCode.NO_MORE_QUESTIONS, () -> service.startQuestion(game.code(), game.hostToken()));
	}

	@Test
	void endingTheGame_closesIt_newPlayersCannotJoin_butPlayersCanStillRejoin() {
		GameCreated game = createGame();
		PlayerSelf me = join(game.code(), "p1", "Samba");

		service.endGame(game.code(), game.hostToken());

		assertEquals(1, notifier.roomEvents(game.code(), RoomEvent.GameEnded.class).size());
		assertGameError(ErrorCode.GAME_FINISHED, () -> service.join(game.code(), "p2", "Lina", null));
		service.join(game.code(), "p1-again", null, me.playerId());
		assertEquals(GameStatus.FINISHED, notifier.lastSnapshot("p1-again").status());
	}

	// ------------------------------------------------------------------ time

	@Test
	void aManualClose_cancelsTheAutomaticClose() {
		GameCreated game = createGame();
		service.startQuestion(game.code(), game.hostToken());
		ScheduledFuture<?> autoClose = service.findGame(game.code()).getAutoCloseTask();

		service.closeQuestion(game.code(), game.hostToken());
		service.onQuestionTimerExpired(game.code(), "q1"); // as if the timer fired anyway

		assertTrue(autoClose.isCancelled());
		assertEquals(1, notifier.roomEvents(game.code(), RoomEvent.QuestionClosed.class).size(),
				"a question is never closed twice");
	}

	@Test
	void theTimer_closesTheQuestionOnce_thenAManualCloseIsRefused() {
		GameCreated game = createGame();
		service.startQuestion(game.code(), game.hostToken());

		service.onQuestionTimerExpired(game.code(), "q1");
		service.onQuestionTimerExpired(game.code(), "q1");

		assertEquals(1, notifier.roomEvents(game.code(), RoomEvent.QuestionClosed.class).size());
		assertGameError(ErrorCode.QUESTION_NOT_OPEN, () -> service.closeQuestion(game.code(), game.hostToken()));
	}

	@Test
	void aLateTimerOfThePreviousQuestion_doesNotCloseTheNextOne() {
		GameCreated game = createGame();
		service.startQuestion(game.code(), game.hostToken());
		service.closeQuestion(game.code(), game.hostToken());
		service.startQuestion(game.code(), game.hostToken());

		service.onQuestionTimerExpired(game.code(), "q1");

		service.join(game.code(), "p1", "Samba", null);
		assertEquals(GameStatus.QUESTION_OPEN, notifier.lastSnapshot("p1").status());
		assertEquals("q2", notifier.lastSnapshot("p1").currentQuestion().questionId());
	}

	@Test
	void theRealTimer_closesTheQuestionWhenTheTimeIsUp() throws InterruptedException {
		service.createGame(HOST, "fast"); // one question of 150 ms
		GameCreated game = notifier.lastPrivate(HOST, GameCreated.class);
		service.startQuestion(game.code(), game.hostToken());

		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
		while (notifier.roomEvents(game.code(), RoomEvent.QuestionClosed.class).isEmpty()
				&& System.nanoTime() < deadline) {
			Thread.sleep(20);
		}
		assertEquals(1, notifier.roomEvents(game.code(), RoomEvent.QuestionClosed.class).size());
	}

	// ------------------------------------------------------------------ isolation

	@Test
	void twoGames_neverShareMessagesNorRights() {
		GameCreated gameA = createGame();
		service.createGame("host-b", "quiz");
		GameCreated gameB = notifier.lastPrivate("host-b", GameCreated.class);
		join(gameA.code(), "player-a", "Samba");
		join(gameB.code(), "player-b", "Lina");

		service.startQuestion(gameA.code(), gameA.hostToken());

		assertTrue(notifier.roomEvents(gameB.code(), RoomEvent.QuestionStarted.class).isEmpty(),
				"game B must not receive game A's question");
		assertEquals(List.of("Lina"), playerNicknames(gameB.code()));
		assertGameError(ErrorCode.INVALID_HOST_TOKEN, () -> service.startQuestion(gameB.code(), gameA.hostToken()));
		assertGameError(ErrorCode.NOT_IN_GAME, () -> service.submitAnswer(gameB.code(), "player-a", "q1", 1));
	}

	// ------------------------------------------------------------------ helpers

	/** Creates a game with the "quiz" theme and connects its host. */
	private GameCreated createGame() {
		service.createGame(HOST, "quiz");
		GameCreated created = notifier.lastPrivate(HOST, GameCreated.class);
		service.hostJoin(created.code(), HOST, created.hostToken());
		return created;
	}

	private PlayerSelf join(String code, String connectionId, String nickname) {
		service.join(code, connectionId, nickname, null);
		return notifier.lastSnapshot(connectionId).you();
	}

	private PlayerView playerView(String code, String publicId) {
		return notifier.lastRoomEvent(code, RoomEvent.PlayersUpdated.class).players().stream()
				.filter(p -> p.publicId().equals(publicId))
				.findFirst()
				.orElseThrow();
	}

	private List<String> playerNicknames(String code) {
		return notifier.lastRoomEvent(code, RoomEvent.PlayersUpdated.class).players().stream()
				.map(PlayerView::nickname)
				.toList();
	}

	private List<LeaderboardEntry> lastLeaderboard(String code) {
		return notifier.lastRoomEvent(code, RoomEvent.QuestionClosed.class).leaderboard();
	}

	private LeaderboardEntry leaderboardEntry(String code, String nickname) {
		return lastLeaderboard(code).stream().filter(e -> e.nickname().equals(nickname)).findFirst().orElseThrow();
	}

	private static void assertGameError(ErrorCode expected, Executable action) {
		GameException exception = assertThrows(GameException.class, action);
		assertEquals(expected, exception.getCode());
	}

	// ------------------------------------------------------------------ test doubles

	/** Two themes: "quiz" (Q1, Q2) and "fast" (one question of 150 ms, for the real timer test). */
	private static final class TestCatalog implements QuestionCatalog {

		private static final List<Theme> THEMES = List.of(
				new Theme("quiz", "Quiz", List.of(Q1, Q2)),
				new Theme("fast", "Fast", List.of(new Question("f1", "Fast", List.of("A", "B"), 0, 1000, 150))));

		@Override
		public List<Theme> themes() {
			return THEMES;
		}

		@Override
		public Optional<Theme> findTheme(String themeId) {
			return THEMES.stream().filter(theme -> theme.id().equals(themeId)).findFirst();
		}

	}

	/** A clock that only moves when the test says so. */
	private static final class MutableClock extends Clock {

		private volatile Instant now;

		MutableClock(Instant start) {
			this.now = start;
		}

		void advanceMillis(long millis) {
			now = now.plusMillis(millis);
		}

		@Override
		public Instant instant() {
			return now;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

	}

	/** Records every message instead of sending it. Thread-safe: the real timer publishes from its own thread. */
	private static final class RecordingNotifier implements GameNotifier {

		record RoomMessage(String gameCode, RoomEvent event) {
		}

		record PrivateMessage(String connectionId, Object payload) {
		}

		final List<RoomMessage> roomMessages = new CopyOnWriteArrayList<>();
		final List<PrivateMessage> privateMessages = new CopyOnWriteArrayList<>();

		@Override
		public void broadcast(String gameCode, RoomEvent event) {
			roomMessages.add(new RoomMessage(gameCode, event));
		}

		@Override
		public void sendGameCreated(String connectionId, GameCreated message) {
			privateMessages.add(new PrivateMessage(connectionId, message));
		}

		@Override
		public void sendSnapshot(String connectionId, GameSnapshot snapshot) {
			privateMessages.add(new PrivateMessage(connectionId, snapshot));
		}

		@Override
		public void sendAnswerProgress(String hostConnectionId, RoomEvent.AnswerProgress progress) {
			privateMessages.add(new PrivateMessage(hostConnectionId, progress));
		}

		<T> List<T> allPrivate(String connectionId, Class<T> type) {
			return privateMessages.stream()
					.filter(message -> message.connectionId().equals(connectionId) && type.isInstance(message.payload()))
					.map(message -> type.cast(message.payload()))
					.toList();
		}

		<T> T lastPrivate(String connectionId, Class<T> type) {
			for (PrivateMessage message : privateMessages.reversed()) {
				if (message.connectionId().equals(connectionId) && type.isInstance(message.payload())) {
					return type.cast(message.payload());
				}
			}
			return fail("No " + type.getSimpleName() + " sent to " + connectionId);
		}

		GameSnapshot lastSnapshot(String connectionId) {
			return lastPrivate(connectionId, GameSnapshot.class);
		}

		<T extends RoomEvent> List<T> roomEvents(String gameCode, Class<T> type) {
			return roomMessages.stream()
					.filter(message -> message.gameCode().equals(gameCode))
					.map(RoomMessage::event)
					.filter(type::isInstance)
					.map(type::cast)
					.toList();
		}

		<T extends RoomEvent> T lastRoomEvent(String gameCode, Class<T> type) {
			List<T> events = roomEvents(gameCode, type);
			if (events.isEmpty()) {
				return fail("No " + type.getSimpleName() + " broadcast to game " + gameCode);
			}
			return events.getLast();
		}

	}

}
