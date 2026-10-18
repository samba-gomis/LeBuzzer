package com.lebuzzer.backend.model;

/**
 * A player's answer to the current question. The points are computed when the answer
 * arrives (they depend on the time left) but only added to the score when the question closes,
 * so nobody can guess who answered right before that.
 */
public record SubmittedAnswer(int choiceIndex, int points) {
}
