package com.lebuzzer.backend.dto;

/** Private answer to /app/game/create, on /user/queue/game. The hostToken is never sent again. */
public record GameCreated(String code, String hostToken) {
}
