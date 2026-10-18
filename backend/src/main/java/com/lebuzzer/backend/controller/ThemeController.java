package com.lebuzzer.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lebuzzer.backend.dto.ThemeView;
import com.lebuzzer.backend.service.QuestionCatalog;

/**
 * Plain HTTP on purpose: the list of themes is static data, asked once on the home page.
 * A WebSocket would bring nothing here.
 */
@RestController
@RequestMapping("/api/themes")
public class ThemeController {

	private final QuestionCatalog questionCatalog;

	public ThemeController(QuestionCatalog questionCatalog) {
		this.questionCatalog = questionCatalog;
	}

	@GetMapping
	public List<ThemeView> listThemes() {
		return questionCatalog.themes().stream()
				.map(theme -> new ThemeView(theme.id(), theme.name(), theme.questions().size()))
				.toList();
	}

}
