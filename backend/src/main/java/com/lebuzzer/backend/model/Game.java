package com.lebuzzer.backend.model;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

/**
 * The state of one game, kept in memory. This class only holds data and simple state changes:
 * the rules (who may do what, and when) live in {@code GameService}.
 *
 * Not thread-safe by itself: {@code GameService} always works on a game inside
 * {@code synchronized (game)}, so each game is its own lock.
 */
public class Game {

	private final String code;
	private final String hostToken;
	private final Theme theme;
	/** By secret playerId. A LinkedHashMap keeps the joining order for the player list. */
	private final Map<String, Player> playersById = new LinkedHashMap<>();
	/** Answers to the current question, by secret playerId. Emptied when the next question opens. */
	private final Map<String, SubmittedAnswer> currentAnswers = new HashMap<>();

	private GameStatus status = GameStatus.LOBBY;
	private int currentQuestionIndex = -1;
	/** Server time (epoch ms) after which the current question no longer accepts answers. */
	private long questionEndsAt;
	private ScheduledFuture<?> autoCloseTask;
	private String hostConnectionId;

	public Game(String code, String hostToken, Theme theme) {
		this.code = code;
		this.hostToken = hostToken;
		this.theme = theme;
	}

	public String getCode() {
		return code;
	}

	public String getHostToken() {
		return hostToken;
	}

	public GameStatus getStatus() {
		return status;
	}

	public Collection<Player> getPlayers() {
		return Collections.unmodifiableCollection(playersById.values());
	}

	public Player findPlayer(String playerId) {
		return playersById.get(playerId);
	}

	public void addPlayer(Player player) {
		playersById.put(player.getPlayerId(), player);
	}

	public int connectedPlayerCount() {
		return (int) playersById.values().stream().filter(Player::isConnected).count();
	}

	// --- questions

	/** The question currently open or last closed, {@code null} before the first one. */
	public Question currentQuestion() {
		return currentQuestionIndex < 0 ? null : theme.questions().get(currentQuestionIndex);
	}

	public int getCurrentQuestionIndex() {
		return currentQuestionIndex;
	}

	public int totalQuestions() {
		return theme.questions().size();
	}

	public boolean hasNextQuestion() {
		return currentQuestionIndex + 1 < theme.questions().size();
	}

	public long getQuestionEndsAt() {
		return questionEndsAt;
	}

	/** Opens the next question; it accepts answers until {@code now + its duration}. */
	public void openNextQuestion(long now) {
		currentQuestionIndex++;
		questionEndsAt = now + currentQuestion().durationMs();
		currentAnswers.clear();
		status = GameStatus.QUESTION_OPEN;
	}

	public void markQuestionClosed() {
		status = GameStatus.QUESTION_CLOSED;
	}

	public void markFinished() {
		status = GameStatus.FINISHED;
	}

	// --- answers

	public SubmittedAnswer findAnswer(String playerId) {
		return currentAnswers.get(playerId);
	}

	public void recordAnswer(String playerId, SubmittedAnswer answer) {
		currentAnswers.put(playerId, answer);
	}

	public int answeredCount() {
		return currentAnswers.size();
	}

	// --- timer and host connection

	public ScheduledFuture<?> getAutoCloseTask() {
		return autoCloseTask;
	}

	public void setAutoCloseTask(ScheduledFuture<?> autoCloseTask) {
		this.autoCloseTask = autoCloseTask;
	}

	public String getHostConnectionId() {
		return hostConnectionId;
	}

	public void setHostConnectionId(String hostConnectionId) {
		this.hostConnectionId = hostConnectionId;
	}

}
