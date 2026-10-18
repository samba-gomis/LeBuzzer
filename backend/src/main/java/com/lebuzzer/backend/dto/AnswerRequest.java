package com.lebuzzer.backend.dto;

/**
 * /app/game/{code}/answer. No player id here: the server knows who answers from the
 * connection itself, so nobody can answer on behalf of someone else.
 */
public record AnswerRequest(String questionId, Integer choiceIndex) {
}
