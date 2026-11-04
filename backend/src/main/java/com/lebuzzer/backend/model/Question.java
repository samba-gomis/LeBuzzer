package com.lebuzzer.backend.model;

import java.util.List;

/**
 * A quiz question as stored in the catalog. {@code correctIndex} never leaves the server
 * while the question is open.
 *
 * @param difficulty label of the level shown to the players, e.g. "Moyen"
 * @param points maximum points for a right answer (see {@code ScoreCalculator})
 * @param durationMs time allowed to answer
 */
public record Question(String id, String text, List<String> choices, int correctIndex, String difficulty, int points,
		long durationMs) {

	public Question {
		choices = List.copyOf(choices);
		if (correctIndex < 0 || correctIndex >= choices.size()) {
			throw new IllegalArgumentException("correctIndex out of range for question " + id);
		}
		if (points <= 0 || durationMs <= 0) {
			throw new IllegalArgumentException("points and durationMs must be positive for question " + id);
		}
	}

}
