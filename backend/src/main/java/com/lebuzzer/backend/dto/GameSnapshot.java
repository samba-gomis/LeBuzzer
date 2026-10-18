package com.lebuzzer.backend.dto;

import java.util.List;

import com.lebuzzer.backend.model.GameStatus;
import com.lebuzzer.backend.model.ParticipantRole;

/**
 * The full current state of a game, sent privately on /user/queue/state after join, host-join
 * and sync. A late joiner or a reconnected client replaces its whole local state with it:
 * events only describe changes, the snapshot describes the present.
 *
 * @param you the player themselves, {@code null} for the host
 * @param currentQuestion the open or last closed question, {@code null} in the lobby
 * @param correctIndex only once the current question is closed, otherwise {@code null}
 * @param serverNow server time when the snapshot was built, to compute the clock offset
 */
public record GameSnapshot(String code, GameStatus status, ParticipantRole role, PlayerSelf you,
		List<PlayerView> players, QuestionView currentQuestion, Integer correctIndex, int answeredCount,
		int connectedPlayers, List<LeaderboardEntry> leaderboard, long serverNow) {
}
