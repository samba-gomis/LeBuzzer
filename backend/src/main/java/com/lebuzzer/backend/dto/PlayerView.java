package com.lebuzzer.backend.dto;

/** A player as everybody sees them: only the public id, never the secret playerId. */
public record PlayerView(String publicId, String nickname, boolean connected, int score) {
}
