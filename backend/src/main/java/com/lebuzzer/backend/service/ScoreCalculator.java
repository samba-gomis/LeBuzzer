package com.lebuzzer.backend.service;

/**
 * Wrong answer: 0. Right answer: between 50 % (answered at the last moment) and 100 %
 * (answered instantly) of the question's points. Kept in one place so the scoring can be
 * changed without touching the game logic.
 */
public final class ScoreCalculator {

	private ScoreCalculator() {
	}

	public static int pointsFor(boolean correct, int questionPoints, long remainingMs, long durationMs) {
		if (!correct) {
			return 0;
		}
		double timeLeftRatio = Math.clamp((double) remainingMs / durationMs, 0.0, 1.0);
		return (int) Math.round(questionPoints * (0.5 + 0.5 * timeLeftRatio));
	}

}
