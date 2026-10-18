package com.lebuzzer.backend.model;

/**
 * Life cycle of a game:
 * LOBBY -> QUESTION_OPEN -> QUESTION_CLOSED -> (QUESTION_OPEN -> QUESTION_CLOSED ...) -> FINISHED.
 */
public enum GameStatus {
	LOBBY,
	QUESTION_OPEN,
	QUESTION_CLOSED,
	FINISHED
}
