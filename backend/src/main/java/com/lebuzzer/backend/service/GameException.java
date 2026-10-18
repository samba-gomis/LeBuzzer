package com.lebuzzer.backend.service;

import com.lebuzzer.backend.dto.ErrorCode;

/**
 * A rule of the game was not respected. Thrown before any state change, so a refused action
 * leaves the game untouched; the controller turns it into a private {@code ErrorMessage}.
 */
public class GameException extends RuntimeException {

	private final ErrorCode code;

	public GameException(ErrorCode code, String message) {
		super(message);
		this.code = code;
	}

	public ErrorCode getCode() {
		return code;
	}

}
