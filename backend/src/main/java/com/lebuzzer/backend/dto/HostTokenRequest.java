package com.lebuzzer.backend.dto;

/** Host actions: /app/game/{code}/host-join, start-question, close-question and end. */
public record HostTokenRequest(String hostToken) {
}
