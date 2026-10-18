package com.lebuzzer.backend.dto;

/** GET /api/themes */
public record ThemeView(String id, String name, int questionCount) {
}
