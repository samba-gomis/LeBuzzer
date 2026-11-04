package com.lebuzzer.backend.service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.lebuzzer.backend.model.Theme;
import com.lebuzzer.backend.repository.QuestionRepository;
import com.lebuzzer.backend.repository.ThemeRepository;

/**
 * The catalogue stored in PostgreSQL, read once when the server starts and then kept in memory:
 * no SQL query is made during a game, and games go on if the database goes down. An inconsistent
 * catalogue (see {@link CatalogAssembler}) stops the startup instead of breaking a game.
 */
@Component
public class DatabaseQuestionCatalog implements QuestionCatalog {

	private static final Logger log = LoggerFactory.getLogger(DatabaseQuestionCatalog.class);

	private final List<Theme> themes;

	public DatabaseQuestionCatalog(ThemeRepository themeRepository, QuestionRepository questionRepository) {
		this.themes = CatalogAssembler.assemble(themeRepository.findAllByOrderByPositionAsc(),
				questionRepository.findAllWithDetails());
		log.info("Question catalogue loaded: {} themes, {} questions", themes.size(),
				themes.stream().mapToInt(theme -> theme.questions().size()).sum());
	}

	@Override
	public List<Theme> themes() {
		return themes;
	}

	@Override
	public Optional<Theme> findTheme(String themeId) {
		return themes.stream().filter(theme -> theme.id().equals(themeId)).findFirst();
	}

}
