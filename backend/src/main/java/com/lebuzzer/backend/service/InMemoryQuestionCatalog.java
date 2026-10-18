package com.lebuzzer.backend.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.lebuzzer.backend.model.Question;
import com.lebuzzer.backend.model.Theme;

/**
 * TEMPORARY: one hard-coded theme so the game can be played before the database exists.
 * To be replaced by the PostgreSQL implementation of {@link QuestionCatalog}.
 * The points (500 / 1000 / 1500) are placeholders until the scoring per difficulty is decided.
 */
@Component
public class InMemoryQuestionCatalog implements QuestionCatalog {

	private static final long DURATION_MS = 20_000;

	private static final Theme GENERAL_KNOWLEDGE = new Theme("general", "Culture générale", List.of(
			new Question("general-1", "Quelle est la capitale de l'Australie ?",
					List.of("Sydney", "Canberra", "Melbourne", "Perth"), 1, 500, DURATION_MS),
			new Question("general-2", "Combien de côtés a un hexagone ?",
					List.of("6", "5", "8", "7"), 0, 500, DURATION_MS),
			new Question("general-3", "Quelle planète est surnommée la planète rouge ?",
					List.of("Vénus", "Jupiter", "Saturne", "Mars"), 3, 1000, DURATION_MS),
			new Question("general-4", "Qui a peint La Joconde ?",
					List.of("Michel-Ange", "Léonard de Vinci", "Raphaël", "Botticelli"), 1, 1000, DURATION_MS),
			new Question("general-5", "En quelle année a eu lieu la prise de la Bastille ?",
					List.of("1789", "1792", "1776", "1815"), 0, 1000, DURATION_MS),
			new Question("general-6", "Quel est le symbole chimique de l'or ?",
					List.of("Ag", "Fe", "Au", "Or"), 2, 1500, DURATION_MS),
			new Question("general-7", "Quel est le plus long fleuve de France ?",
					List.of("La Seine", "Le Rhône", "La Garonne", "La Loire"), 3, 1500, DURATION_MS)));

	private static final List<Theme> THEMES = List.of(GENERAL_KNOWLEDGE);

	@Override
	public List<Theme> themes() {
		return THEMES;
	}

	@Override
	public Optional<Theme> findTheme(String themeId) {
		return THEMES.stream().filter(theme -> theme.id().equals(themeId)).findFirst();
	}

}
