package com.lebuzzer.backend.dto;

/**
 * @param rank same score, same rank (1, 1, 3...)
 * @param lastPoints points won at the last closed question
 */
public record LeaderboardEntry(int rank, String publicId, String nickname, int score, int lastPoints) {
}
