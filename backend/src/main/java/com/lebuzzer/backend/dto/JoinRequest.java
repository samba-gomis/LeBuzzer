package com.lebuzzer.backend.dto;

/**
 * /app/game/{code}/join
 *
 * @param playerId the secret id received at the first join; sent again to rejoin after a disconnection
 */
public record JoinRequest(String nickname, String playerId) {
}
