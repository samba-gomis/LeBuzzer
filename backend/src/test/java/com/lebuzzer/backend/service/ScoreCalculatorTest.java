package com.lebuzzer.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ScoreCalculatorTest {

	@Test
	void aWrongAnswer_isWorthNothing() {
		assertEquals(0, ScoreCalculator.pointsFor(false, 1000, 20_000, 20_000));
	}

	@Test
	void aRightAnswer_isWorthBetweenHalfAndAllThePoints() {
		assertEquals(1000, ScoreCalculator.pointsFor(true, 1000, 20_000, 20_000)); // instant
		assertEquals(750, ScoreCalculator.pointsFor(true, 1000, 10_000, 20_000)); // half the time left
		assertEquals(500, ScoreCalculator.pointsFor(true, 1000, 0, 20_000)); // last moment
	}

	@Test
	void theTimeLeft_isClampedToTheQuestionDuration() {
		assertEquals(1000, ScoreCalculator.pointsFor(true, 1000, 25_000, 20_000));
		assertEquals(500, ScoreCalculator.pointsFor(true, 1000, -1_000, 20_000));
	}

}
