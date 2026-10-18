package com.lebuzzer.backend.model;

/**
 * A player of one game. Two identifiers on purpose:
 * <ul>
 * <li>{@code playerId}: secret, only sent to that player; it lets them rejoin after a disconnection;</li>
 * <li>{@code publicId}: shown to everybody (player list, leaderboard).</li>
 * </ul>
 * If the secret id were broadcast, anyone could rejoin as someone else.
 */
public class Player {

	private final String playerId;
	private final String publicId;
	private final String nickname;
	private int score;
	/** Points won at the last closed question (0 if no answer or a wrong one). */
	private int lastPoints;
	/** Current WebSocket connection, {@code null} while the player is disconnected. */
	private String connectionId;

	public Player(String playerId, String publicId, String nickname) {
		this.playerId = playerId;
		this.publicId = publicId;
		this.nickname = nickname;
	}

	public String getPlayerId() {
		return playerId;
	}

	public String getPublicId() {
		return publicId;
	}

	public String getNickname() {
		return nickname;
	}

	public int getScore() {
		return score;
	}

	public int getLastPoints() {
		return lastPoints;
	}

	public String getConnectionId() {
		return connectionId;
	}

	public boolean isConnected() {
		return connectionId != null;
	}

	public void connect(String connectionId) {
		this.connectionId = connectionId;
	}

	public void disconnect() {
		this.connectionId = null;
	}

	public void recordQuestionResult(int points) {
		this.score += points;
		this.lastPoints = points;
	}

}
