package com.lebuzzer.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.lebuzzer.backend.entity.DifficultyEntity;
import com.lebuzzer.backend.entity.QuestionEntity;
import com.lebuzzer.backend.entity.ThemeEntity;
import com.lebuzzer.backend.model.Question;
import com.lebuzzer.backend.model.Theme;

/** No database here: the entities are built by hand, the way the repositories would return them. */
class CatalogAssemblerTest {

	private static final DifficultyEntity EASY = new DifficultyEntity("EASY", "Facile", 500, 20_000, 1);
	private static final DifficultyEntity HARD = new DifficultyEntity("HARD", "Difficile", 1500, 25_000, 3);
	private static final List<String> CHOICES = List.of("A", "B", "C", "D");

	@Test
	void assemblesThemesAndQuestionsInPositionOrder() {
		ThemeEntity a = new ThemeEntity("a", "Theme A", 1);
		ThemeEntity b = new ThemeEntity("b", "Theme B", 2);
		List<QuestionEntity> questions = new ArrayList<>();
		questions.addAll(sevenQuestions(b));
		questions.addAll(sevenQuestions(a).reversed());

		List<Theme> themes = CatalogAssembler.assemble(List.of(b, a), questions);

		assertEquals(List.of("a", "b"), themes.stream().map(Theme::id).toList());
		assertEquals(List.of("a-1", "a-2", "a-3", "a-4", "a-5", "a-6", "a-7"),
				themes.getFirst().questions().stream().map(Question::id).toList());
	}

	@Test
	void convertsTheCorrectPositionAndAppliesTheDifficulty() {
		ThemeEntity theme = new ThemeEntity("t", "Theme", 1);
		List<QuestionEntity> questions = new ArrayList<>(sevenQuestions(theme));
		questions.set(6, new QuestionEntity("t-7", theme, 7, HARD, "Hard one", 4, List.of("W", "X", "Y", "Z")));

		List<Question> assembled = CatalogAssembler.assemble(List.of(theme), questions).getFirst().questions();

		Question hard = assembled.get(6);
		assertEquals(3, hard.correctIndex());
		assertEquals(1500, hard.points());
		assertEquals(25_000, hard.durationMs());
		assertEquals(List.of("W", "X", "Y", "Z"), hard.choices());
		assertEquals(0, assembled.getFirst().correctIndex()); // correctPosition 1
	}

	@Test
	void refusesAnEmptyCatalog() {
		assertRefused("Catalog has no theme", List.of(), List.of());
	}

	@Test
	void refusesThemeWithoutAnyQuestion() {
		ThemeEntity general = new ThemeEntity("general", "Culture générale", 1);
		ThemeEntity anime = new ThemeEntity("anime", "Anime (manga)", 2);

		assertRefused("Theme anime has 0 questions, expected 7", List.of(general, anime), sevenQuestions(general));
	}

	@Test
	void refusesThemeWithoutSevenQuestions() {
		ThemeEntity anime = new ThemeEntity("anime", "Anime (manga)", 1);

		assertRefused("Theme anime has 6 questions, expected 7", List.of(anime), sevenQuestions(anime).subList(0, 6));
	}

	@Test
	void refusesQuestionWithoutFourChoices() {
		ThemeEntity sport = new ThemeEntity("sport", "Sport", 1);
		List<QuestionEntity> questions = new ArrayList<>(sevenQuestions(sport));
		questions.set(2, new QuestionEntity("sport-3", sport, 3, EASY, "Three choices", 1, List.of("A", "B", "C")));

		assertRefused("Question sport-3 has 3 choices, expected 4", List.of(sport), questions);
	}

	// ------------------------------------------------------------------ helpers

	/** Seven valid questions "theme-1" to "theme-7", in position order, the right choice being A. */
	private static List<QuestionEntity> sevenQuestions(ThemeEntity theme) {
		return IntStream.rangeClosed(1, 7)
				.mapToObj(position -> new QuestionEntity(theme.getId() + "-" + position, theme, position, EASY,
						"Question " + position, 1, CHOICES))
				.toList();
	}

	private static void assertRefused(String expectedMessage, List<ThemeEntity> themes,
			List<QuestionEntity> questions) {
		IllegalStateException exception = assertThrows(IllegalStateException.class,
				() -> CatalogAssembler.assemble(themes, questions));
		assertEquals(expectedMessage, exception.getMessage());
	}

}
