package com.lebuzzer.backend.service;

import java.util.List;
import java.util.Optional;

import com.lebuzzer.backend.model.Theme;

/**
 * Where the questions come from. The game only depends on this interface: today an in-memory
 * implementation, later the PostgreSQL one, without changing anything else.
 * A game reads its theme once, at creation; nothing is read from the catalog during play.
 */
public interface QuestionCatalog {

	List<Theme> themes();

	Optional<Theme> findTheme(String themeId);

}
