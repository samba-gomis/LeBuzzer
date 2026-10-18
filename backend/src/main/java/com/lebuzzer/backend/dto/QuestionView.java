package com.lebuzzer.backend.dto;

import java.util.List;

/**
 * A question as sent to clients. There is deliberately no correct answer in this type,
 * so it cannot leak while the question is open.
 *
 * @param index 0-based position in the game
 * @param endsAt server time (epoch ms) at which answers stop being accepted
 */
public record QuestionView(String questionId, int index, int total, String text, List<String> choices,
		int points, long durationMs, long endsAt) {
}
