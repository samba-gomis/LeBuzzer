package com.lebuzzer.backend.controller;

import java.util.Locale;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lebuzzer.backend.dto.GameMessageMetrics;
import com.lebuzzer.backend.service.MessageMetrics;

/** GET /api/metrics/{code}: the messages really exchanged by one game (404 if nothing was counted). */
@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

	private final MessageMetrics messageMetrics;

	public MetricsController(MessageMetrics messageMetrics) {
		this.messageMetrics = messageMetrics;
	}

	@GetMapping("/{code}")
	public ResponseEntity<GameMessageMetrics> forGame(@PathVariable String code) {
		return ResponseEntity.of(messageMetrics.forGame(code.strip().toUpperCase(Locale.ROOT)));
	}

}
