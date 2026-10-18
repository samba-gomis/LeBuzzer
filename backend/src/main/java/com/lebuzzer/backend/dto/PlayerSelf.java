package com.lebuzzer.backend.dto;

/**
 * What a player knows about themselves (only in their own snapshot).
 *
 * @param playerId secret: to keep in sessionStorage and send again to rejoin
 * @param choiceIndex answer given to the current question, {@code null} if none
 */
public record PlayerSelf(String playerId, String publicId, String nickname, int score, int lastPoints,
		Integer choiceIndex) {
}
