package com.lebuzzer.backend.dto;

import java.util.Map;

/** Messages counted in one direction, in total and per type (e.g. "SEND answer", "MESSAGE snapshot"). */
public record DirectionCounts(long total, Map<String, Long> byType) {
}
