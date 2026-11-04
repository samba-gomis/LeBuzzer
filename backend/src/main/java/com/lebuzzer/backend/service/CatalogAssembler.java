package com.lebuzzer.backend.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.lebuzzer.backend.entity.DifficultyEntity;
import com.lebuzzer.backend.entity.QuestionEntity;
import com.lebuzzer.backend.entity.ThemeEntity;
import com.lebuzzer.backend.model.Question;
import com.lebuzzer.backend.model.Theme;

/**
 * Turns the rows read from the database into the {@link Theme} and {@link Question} records the
 * game uses, and refuses an inconsistent catalogue with a clear message. "Exactly 7 questions per
 * theme" and "exactly 4 choices per question" are checked here because they are not simple to
 * write as SQL constraints. The only place where positions counted from 1 (database) become
 * indexes counted from 0 (game).
 */
public final class CatalogAssembler {

	public static final int QUESTIONS_PER_THEME = 7;
	public static final int CHOICES_PER_QUESTION = 4;

	private CatalogAssembler() {
	}

	/** @throws IllegalStateException if the catalogue breaks one of the rules above */
	public static List<Theme> assemble(List<ThemeEntity> themes, List<QuestionEntity> questions) {
		if (themes.isEmpty()) {
			throw new IllegalStateException("Catalog has no theme");
		}
		Map<String, List<QuestionEntity>> questionsByTheme = questions.stream()
				.collect(Collectors.groupingBy(question -> question.getTheme().getId()));
		return themes.stream()
				.sorted(Comparator.comparingInt(ThemeEntity::getPosition))
				.map(theme -> toTheme(theme, questionsByTheme.getOrDefault(theme.getId(), List.of())))
				.toList();
	}

	private static Theme toTheme(ThemeEntity theme, List<QuestionEntity> questions) {
		if (questions.size() != QUESTIONS_PER_THEME) {
			throw new IllegalStateException("Theme " + theme.getId() + " has " + questions.size()
					+ " questions, expected " + QUESTIONS_PER_THEME);
		}
		List<Question> converted = questions.stream()
				.sorted(Comparator.comparingInt(QuestionEntity::getPosition))
				.map(CatalogAssembler::toQuestion)
				.toList();
		return new Theme(theme.getId(), theme.getName(), converted);
	}

	private static Question toQuestion(QuestionEntity question) {
		List<String> choices = question.getChoices();
		if (choices.size() != CHOICES_PER_QUESTION) {
			throw new IllegalStateException("Question " + question.getId() + " has " + choices.size()
					+ " choices, expected " + CHOICES_PER_QUESTION);
		}
		DifficultyEntity difficulty = question.getDifficulty();
		return new Question(question.getId(), question.getText(), choices,
				question.getCorrectPosition() - 1, difficulty.getLabel(), difficulty.getPoints(),
				difficulty.getDurationMs());
	}

}
