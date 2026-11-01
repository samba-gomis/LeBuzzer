package com.lebuzzer.backend.dto;

import java.util.List;

/**
 * The game events, broadcast on /topic/game/{code} (except {@link AnswerProgress}, see below). A single
 * topic per game: each message carries a {@code type} field, so the frontend can tell them apart
 * (discriminated union in TypeScript). Each record sets its own type through its short constructor.
 */
public sealed interface RoomEvent {

	Type type();

	enum Type {
		PLAYERS_UPDATED,
		QUESTION_STARTED,
		ANSWER_PROGRESS,
		QUESTION_CLOSED,
		GAME_ENDED
	}

	record PlayersUpdated(Type type, List<PlayerView> players) implements RoomEvent {

		public PlayersUpdated(List<PlayerView> players) {
			this(Type.PLAYERS_UPDATED, players);
		}

	}

	/**
	 * Never contains the correct answer (see {@link QuestionView}). The leaderboard is the one before this
	 * question: players who joined since the last close appear in it, and every player can display it.
	 */
	record QuestionStarted(Type type, QuestionView question, long serverNow, List<LeaderboardEntry> leaderboard)
			implements RoomEvent {

		public QuestionStarted(QuestionView question, long serverNow, List<LeaderboardEntry> leaderboard) {
			this(Type.QUESTION_STARTED, question, serverNow, leaderboard);
		}

	}

	/** The only event not broadcast to the room: sent to the host alone, on /user/queue/progress. */
	record AnswerProgress(Type type, int answeredCount, int connectedPlayers) implements RoomEvent {

		public AnswerProgress(int answeredCount, int connectedPlayers) {
			this(Type.ANSWER_PROGRESS, answeredCount, connectedPlayers);
		}

	}

	record QuestionClosed(Type type, String questionId, int correctIndex, List<LeaderboardEntry> leaderboard)
			implements RoomEvent {

		public QuestionClosed(String questionId, int correctIndex, List<LeaderboardEntry> leaderboard) {
			this(Type.QUESTION_CLOSED, questionId, correctIndex, leaderboard);
		}

	}

	record GameEnded(Type type, List<LeaderboardEntry> leaderboard) implements RoomEvent {

		public GameEnded(List<LeaderboardEntry> leaderboard) {
			this(Type.GAME_ENDED, leaderboard);
		}

	}

}
