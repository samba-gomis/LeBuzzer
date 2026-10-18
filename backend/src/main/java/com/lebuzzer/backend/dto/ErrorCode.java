package com.lebuzzer.backend.dto;

/** Sent in {@link ErrorMessage}; the frontend maps each code to a French message. */
public enum ErrorCode {
	GAME_NOT_FOUND,
	THEME_NOT_FOUND,
	INVALID_NICKNAME,
	NICKNAME_TAKEN,
	INVALID_HOST_TOKEN,
	/** The connection has not joined this game (or is not a player of it). */
	NOT_IN_GAME,
	GAME_FINISHED,
	QUESTION_ALREADY_OPEN,
	NO_MORE_QUESTIONS,
	/** Manual close asked while no question is open. */
	QUESTION_NOT_OPEN,
	/** Answer received after the deadline, after a close, or for another question. */
	QUESTION_CLOSED,
	ALREADY_ANSWERED,
	INVALID_CHOICE,
	/** Unreadable or empty message. */
	INVALID_REQUEST
}
