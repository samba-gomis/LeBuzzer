package com.lebuzzer.backend.dto;

/** Private error, sent on /user/queue/errors to the connection that caused it only. */
public record ErrorMessage(ErrorCode code, String message) {
}
