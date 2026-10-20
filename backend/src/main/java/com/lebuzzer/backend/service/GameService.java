package com.lebuzzer.backend.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.lebuzzer.backend.dto.ErrorCode;
import com.lebuzzer.backend.dto.GameCreated;
import com.lebuzzer.backend.dto.GameSnapshot;
import com.lebuzzer.backend.dto.LeaderboardEntry;
import com.lebuzzer.backend.dto.PlayerSelf;
import com.lebuzzer.backend.dto.PlayerView;
import com.lebuzzer.backend.dto.QuestionView;
import com.lebuzzer.backend.dto.RoomEvent;
import com.lebuzzer.backend.model.Game;
import com.lebuzzer.backend.model.GameStatus;
import com.lebuzzer.backend.model.ParticipantRole;
import com.lebuzzer.backend.model.Player;
import com.lebuzzer.backend.model.Question;
import com.lebuzzer.backend.model.SubmittedAnswer;
import com.lebuzzer.backend.model.Theme;

/**
 * All the rules of the game.
 *
 * Concurrency: each game is its own lock ({@code synchronized (game)}). Two answers to the same
 * game are handled one after the other; two different games never wait for each other.
 *
 * Messages are published while holding that lock, so clients receive them in the exact order of
 * the state changes: a snapshot can never arrive after an event that is newer than it.
 */
@Service
public class GameService {

	private static final Logger log = LoggerFactory.getLogger(GameService.class);

	private static final int MAX_NICKNAME_LENGTH = 20;
	private static final int MAX_CODE_ATTEMPTS = 100;
	/** Delay before logging the message counts of a finished game, so that its last messages are counted. */
	private static final long METRICS_LOG_DELAY_MS = 2_000;

	/** Active games by code. Concurrent map: many connections create and look up games at once. */
	private final Map<String, Game> games = new ConcurrentHashMap<>();

	/** Who is behind each WebSocket connection; used to identify players and to handle disconnections. */
	private final Map<String, ConnectionInfo> connections = new ConcurrentHashMap<>();

	private final QuestionCatalog questionCatalog;
	private final CodeGenerator codeGenerator;
	private final GameNotifier notifier;
	private final Clock clock;
	private final ScheduledExecutorService questionTimer;
	private final MessageMetrics messageMetrics;

	public GameService(QuestionCatalog questionCatalog, CodeGenerator codeGenerator, GameNotifier notifier,
			Clock clock, ScheduledExecutorService questionTimer, MessageMetrics messageMetrics) {
		this.questionCatalog = questionCatalog;
		this.codeGenerator = codeGenerator;
		this.notifier = notifier;
		this.clock = clock;
		this.questionTimer = questionTimer;
		this.messageMetrics = messageMetrics;
	}

	// ------------------------------------------------------------------ create

	public void createGame(String connectionId, String themeId) {
		Theme theme = questionCatalog.findTheme(themeId)
				.orElseThrow(() -> new GameException(ErrorCode.THEME_NOT_FOUND, "Unknown theme: " + themeId));
		Game game = registerWithUniqueCode(theme);
		log.info("Game {} created with theme '{}'", game.getCode(), theme.id());
		notifier.sendGameCreated(connectionId, new GameCreated(game.getCode(), game.getHostToken()));
	}

	private Game registerWithUniqueCode(Theme theme) {
		for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
			Game candidate = new Game(codeGenerator.nextCode(), UUID.randomUUID().toString(), theme);
			// putIfAbsent is atomic: two games created at the same moment can never share a code.
			if (games.putIfAbsent(candidate.getCode(), candidate) == null) {
				return candidate;
			}
		}
		throw new IllegalStateException("No free game code found after " + MAX_CODE_ATTEMPTS + " attempts");
	}

	// ------------------------------------------------------------------ join, host-join, sync

	/**
	 * First join (nickname) or rejoin after a disconnection ({@code playerId} received the first time).
	 * A rejoining player gets back their score and their answer to the current question.
	 */
	public void join(String code, String connectionId, String nickname, String playerId) {
		Game game = requireGame(code);
		releasePreviousParticipant(connectionId, game.getCode(), ParticipantRole.PLAYER, playerId);
		synchronized (game) {
			Player player = playerId == null ? null : game.findPlayer(playerId);
			if (player == null) {
				player = addNewPlayer(game, nickname);
			} else if (player.getConnectionId() != null && !player.getConnectionId().equals(connectionId)) {
				// The player came back before their old connection was detected as dead: forget the old one.
				connections.remove(player.getConnectionId());
			}
			player.connect(connectionId);
			connections.put(connectionId, ConnectionInfo.player(game.getCode(), player.getPlayerId()));
			notifier.sendSnapshot(connectionId, snapshot(game, ParticipantRole.PLAYER, player));
			broadcastPlayers(game);
		}
	}

	/** The host connects, or reconnects, to their game. The hostToken proves it is really them. */
	public void hostJoin(String code, String connectionId, String hostToken) {
		Game game = requireGame(code);
		requireHostToken(game, hostToken);
		releasePreviousParticipant(connectionId, game.getCode(), ParticipantRole.HOST, null);
		synchronized (game) {
			game.setHostConnectionId(connectionId);
			connections.put(connectionId, ConnectionInfo.host(game.getCode()));
			notifier.sendSnapshot(connectionId, snapshot(game, ParticipantRole.HOST, null));
		}
	}

	/** Sends the current state again to a connection that already joined. */
	public void sync(String code, String connectionId) {
		Game game = requireGame(code);
		ConnectionInfo info = connections.get(connectionId);
		if (info == null || !info.gameCode().equals(game.getCode())) {
			throw new GameException(ErrorCode.NOT_IN_GAME, "This connection has not joined game " + game.getCode());
		}
		synchronized (game) {
			Player self = info.role() == ParticipantRole.PLAYER ? game.findPlayer(info.playerId()) : null;
			notifier.sendSnapshot(connectionId, snapshot(game, info.role(), self));
		}
	}

	// ------------------------------------------------------------------ answers

	public void submitAnswer(String code, String connectionId, String questionId, Integer choiceIndex) {
		Game game = requireGame(code);
		// The player is found from the connection, never from the message: nobody can answer for someone else.
		ConnectionInfo info = connections.get(connectionId);
		if (info == null || info.role() != ParticipantRole.PLAYER || !info.gameCode().equals(game.getCode())) {
			throw new GameException(ErrorCode.NOT_IN_GAME, "This connection is not a player of game " + game.getCode());
		}
		synchronized (game) {
			Question question = game.currentQuestion();
			long now = clock.millis();
			// The server clock is the only one that counts: after endsAt the answer is refused,
			// even if the automatic close has not run yet.
			if (game.getStatus() != GameStatus.QUESTION_OPEN || !question.id().equals(questionId)
					|| now > game.getQuestionEndsAt()) {
				throw new GameException(ErrorCode.QUESTION_CLOSED, "This question no longer accepts answers");
			}
			if (game.findAnswer(info.playerId()) != null) {
				throw new GameException(ErrorCode.ALREADY_ANSWERED, "Only the first answer counts");
			}
			if (choiceIndex == null || choiceIndex < 0 || choiceIndex >= question.choices().size()) {
				throw new GameException(ErrorCode.INVALID_CHOICE, "No such choice: " + choiceIndex);
			}
			int points = ScoreCalculator.pointsFor(choiceIndex == question.correctIndex(), question.points(),
					game.getQuestionEndsAt() - now, question.durationMs());
			game.recordAnswer(info.playerId(), new SubmittedAnswer(choiceIndex, points));
			notifier.broadcast(game.getCode(), answerProgress(game));
		}
	}

	// ------------------------------------------------------------------ host actions

	public void startQuestion(String code, String hostToken) {
		Game game = requireGame(code);
		requireHostToken(game, hostToken);
		synchronized (game) {
			if (game.getStatus() == GameStatus.QUESTION_OPEN) {
				throw new GameException(ErrorCode.QUESTION_ALREADY_OPEN, "Close the current question first");
			}
			if (game.getStatus() == GameStatus.FINISHED) {
				throw new GameException(ErrorCode.GAME_FINISHED, "This game is over");
			}
			if (!game.hasNextQuestion()) {
				throw new GameException(ErrorCode.NO_MORE_QUESTIONS, "All questions have been played");
			}
			long now = clock.millis();
			game.openNextQuestion(now);
			scheduleAutoClose(game, game.currentQuestion().id(), game.getQuestionEndsAt() - now);
			notifier.broadcast(game.getCode(), new RoomEvent.QuestionStarted(questionView(game), now));
		}
	}

	public void closeQuestion(String code, String hostToken) {
		Game game = requireGame(code);
		requireHostToken(game, hostToken);
		synchronized (game) {
			if (game.getStatus() != GameStatus.QUESTION_OPEN) {
				throw new GameException(ErrorCode.QUESTION_NOT_OPEN, "No question is open");
			}
			closeCurrentQuestion(game);
		}
	}

	public void endGame(String code, String hostToken) {
		Game game = requireGame(code);
		requireHostToken(game, hostToken);
		synchronized (game) {
			if (game.getStatus() == GameStatus.FINISHED) {
				throw new GameException(ErrorCode.GAME_FINISHED, "This game is already over");
			}
			if (game.getStatus() == GameStatus.QUESTION_OPEN) {
				closeCurrentQuestion(game);
			}
			game.markFinished();
			notifier.broadcast(game.getCode(), new RoomEvent.GameEnded(leaderboard(game)));
			log.info("Game {} finished", game.getCode());
			String finishedCode = game.getCode();
			questionTimer.schedule(() -> messageMetrics.logSummary(finishedCode), METRICS_LOG_DELAY_MS,
					TimeUnit.MILLISECONDS);
		}
	}

	// ------------------------------------------------------------------ timer

	private void scheduleAutoClose(Game game, String questionId, long delayMs) {
		String code = game.getCode();
		ScheduledFuture<?> task = questionTimer.schedule(() -> {
			try {
				onQuestionTimerExpired(code, questionId);
			} catch (RuntimeException e) {
				// An exception would otherwise be swallowed silently by the executor.
				log.error("Automatic close failed for game {} question {}", code, questionId, e);
			}
		}, delayMs, TimeUnit.MILLISECONDS);
		game.setAutoCloseTask(task);
	}

	/** Run by the timer when a question's time is up (package-private so tests can trigger it). */
	void onQuestionTimerExpired(String code, String questionId) {
		Game game = games.get(code);
		if (game == null) {
			return;
		}
		synchronized (game) {
			// The host may already have closed this question, or even opened the next one:
			// then this timer has nothing left to do. A question is never closed twice.
			Question current = game.currentQuestion();
			if (game.getStatus() == GameStatus.QUESTION_OPEN && current.id().equals(questionId)) {
				closeCurrentQuestion(game);
			}
		}
	}

	/** Must be called while holding the game's lock, with a question open. */
	private void closeCurrentQuestion(Game game) {
		ScheduledFuture<?> task = game.getAutoCloseTask();
		if (task != null) {
			// Closed by the host before the end of the time: the timer must not fire later.
			task.cancel(false);
		}
		for (Player player : game.getPlayers()) {
			SubmittedAnswer answer = game.findAnswer(player.getPlayerId());
			player.recordQuestionResult(answer == null ? 0 : answer.points());
		}
		game.markQuestionClosed();
		Question question = game.currentQuestion();
		notifier.broadcast(game.getCode(),
				new RoomEvent.QuestionClosed(question.id(), question.correctIndex(), leaderboard(game)));
	}

	// ------------------------------------------------------------------ disconnection

	/** Called when a WebSocket connection closes (tab closed, network lost...). */
	public void handleDisconnect(String connectionId) {
		// remove() makes this safe to call twice: Spring may report the same disconnection more than once.
		ConnectionInfo info = connections.remove(connectionId);
		if (info == null) {
			return;
		}
		Game game = games.get(info.gameCode());
		if (game == null) {
			return;
		}
		synchronized (game) {
			if (info.role() == ParticipantRole.HOST) {
				// The game goes on without the host, who can come back with host-join.
				if (connectionId.equals(game.getHostConnectionId())) {
					game.setHostConnectionId(null);
				}
				return;
			}
			Player player = game.findPlayer(info.playerId());
			// If the player is already back on a newer connection, this old one does not matter.
			if (player != null && connectionId.equals(player.getConnectionId())) {
				player.disconnect();
				broadcastPlayers(game);
			}
		}
	}

	/**
	 * One connection = one participant. If this connection was already someone else
	 * (e.g. the player of another game), that participant is disconnected first.
	 * Done before taking the new game's lock: we never hold two game locks at once (no deadlock).
	 */
	private void releasePreviousParticipant(String connectionId, String gameCode, ParticipantRole role,
			String playerId) {
		ConnectionInfo previous = connections.get(connectionId);
		if (previous != null && !previous.isSameParticipant(gameCode, role, playerId)) {
			handleDisconnect(connectionId);
		}
	}

	// ------------------------------------------------------------------ helpers

	private Game requireGame(String code) {
		String normalized = code == null ? "" : code.strip().toUpperCase(Locale.ROOT);
		Game game = games.get(normalized);
		if (game == null) {
			throw new GameException(ErrorCode.GAME_NOT_FOUND, "No game with code " + normalized);
		}
		return game;
	}

	private void requireHostToken(Game game, String hostToken) {
		if (hostToken == null || !game.getHostToken().equals(hostToken)) {
			throw new GameException(ErrorCode.INVALID_HOST_TOKEN, "Only the host can do this");
		}
	}

	private Player addNewPlayer(Game game, String nickname) {
		if (game.getStatus() == GameStatus.FINISHED) {
			throw new GameException(ErrorCode.GAME_FINISHED, "This game is over");
		}
		String cleaned = nickname == null ? "" : nickname.strip();
		// Counted in characters as a person sees them (an emoji counts as one).
		int length = cleaned.codePointCount(0, cleaned.length());
		if (length == 0 || length > MAX_NICKNAME_LENGTH) {
			throw new GameException(ErrorCode.INVALID_NICKNAME, "A nickname has 1 to 20 characters");
		}
		boolean taken = game.getPlayers().stream().anyMatch(p -> p.getNickname().equalsIgnoreCase(cleaned));
		if (taken) {
			throw new GameException(ErrorCode.NICKNAME_TAKEN, "This nickname is already used in this game");
		}
		Player player = new Player(UUID.randomUUID().toString(), UUID.randomUUID().toString(), cleaned);
		game.addPlayer(player);
		return player;
	}

	private void broadcastPlayers(Game game) {
		notifier.broadcast(game.getCode(), new RoomEvent.PlayersUpdated(playerViews(game)));
		// During a question, the number of connected players is part of the answer progress.
		if (game.getStatus() == GameStatus.QUESTION_OPEN) {
			notifier.broadcast(game.getCode(), answerProgress(game));
		}
	}

	private RoomEvent.AnswerProgress answerProgress(Game game) {
		return new RoomEvent.AnswerProgress(game.answeredCount(), game.connectedPlayerCount());
	}

	private List<PlayerView> playerViews(Game game) {
		return game.getPlayers().stream()
				.map(p -> new PlayerView(p.getPublicId(), p.getNickname(), p.isConnected(), p.getScore()))
				.toList();
	}

	private QuestionView questionView(Game game) {
		Question question = game.currentQuestion();
		return new QuestionView(question.id(), game.getCurrentQuestionIndex(), game.totalQuestions(),
				question.text(), question.choices(), question.points(), question.durationMs(),
				game.getQuestionEndsAt());
	}

	/** Highest score first; same score, same rank (1, 1, 3...). */
	private List<LeaderboardEntry> leaderboard(Game game) {
		List<Player> ranked = new ArrayList<>(game.getPlayers());
		ranked.sort(Comparator.comparingInt(Player::getScore).reversed()
				.thenComparing(Player::getNickname, String.CASE_INSENSITIVE_ORDER));
		List<LeaderboardEntry> entries = new ArrayList<>();
		int rank = 0;
		for (int i = 0; i < ranked.size(); i++) {
			Player player = ranked.get(i);
			if (i == 0 || player.getScore() != ranked.get(i - 1).getScore()) {
				rank = i + 1;
			}
			entries.add(new LeaderboardEntry(rank, player.getPublicId(), player.getNickname(), player.getScore(),
					player.getLastPoints()));
		}
		return entries;
	}

	private GameSnapshot snapshot(Game game, ParticipantRole role, Player self) {
		Question question = game.currentQuestion();
		boolean answerRevealed = question != null
				&& (game.getStatus() == GameStatus.QUESTION_CLOSED || game.getStatus() == GameStatus.FINISHED);
		PlayerSelf you = null;
		if (self != null) {
			SubmittedAnswer answer = game.findAnswer(self.getPlayerId());
			you = new PlayerSelf(self.getPlayerId(), self.getPublicId(), self.getNickname(), self.getScore(),
					self.getLastPoints(), answer == null ? null : answer.choiceIndex());
		}
		return new GameSnapshot(game.getCode(), game.getStatus(), role, you, playerViews(game),
				question == null ? null : questionView(game), answerRevealed ? question.correctIndex() : null,
				game.answeredCount(), game.connectedPlayerCount(), leaderboard(game), clock.millis());
	}

	/** For tests only. */
	Game findGame(String code) {
		return games.get(code);
	}

}
