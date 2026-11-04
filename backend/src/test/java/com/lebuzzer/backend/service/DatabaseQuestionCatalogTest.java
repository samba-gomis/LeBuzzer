package com.lebuzzer.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import com.lebuzzer.backend.model.Question;
import com.lebuzzer.backend.model.Theme;

/** Against the real PostgreSQL database, filled by schema.sql and data.sql at startup (as in the CI). */
@SpringBootTest
class DatabaseQuestionCatalogTest {

	@Autowired
	private QuestionCatalog catalog;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private JdbcClient jdbcClient;

	@Test
	void loadsFourThemesInDisplayOrder() {
		List<Theme> themes = catalog.themes();

		assertEquals(List.of("general", "sciences", "sport", "anime"), themes.stream().map(Theme::id).toList());
		assertEquals(List.of("Culture générale", "Sciences et technologies", "Sport", "Anime (manga)"),
				themes.stream().map(Theme::name).toList());
	}

	@Test
	void everyThemeHasSevenQuestionsOfFourChoicesWithTheScoringOfTheirPosition() {
		for (Theme theme : catalog.themes()) {
			List<Question> questions = theme.questions();

			assertEquals(IntStream.rangeClosed(1, 7).mapToObj(position -> theme.id() + "-" + position).toList(),
					questions.stream().map(Question::id).toList());
			for (Question question : questions) {
				assertEquals(4, Set.copyOf(question.choices()).size(), question.id() + " needs 4 distinct choices");
			}
			assertEquals(List.of(500, 500, 1000, 1000, 1000, 1500, 1500),
					questions.stream().map(Question::points).toList(), theme.id());
			assertEquals(List.of(20_000L, 20_000L, 20_000L, 20_000L, 20_000L, 25_000L, 25_000L),
					questions.stream().map(Question::durationMs).toList(), theme.id());
		}
	}

	@Test
	void keepsTheExistingGeneralKnowledgeQuestions() {
		Question first = catalog.findTheme("general").orElseThrow().questions().getFirst();

		assertEquals("general-1", first.id());
		assertEquals("Quelle est la capitale de l'Australie ?", first.text());
		assertEquals(List.of("Sydney", "Canberra", "Melbourne", "Perth"), first.choices());
		assertEquals("Canberra", first.choices().get(first.correctIndex()));
	}

	@Test
	void findsAThemeByIdAndNothingForAnUnknownId() {
		assertEquals(7, catalog.findTheme("anime").orElseThrow().questions().size());
		assertTrue(catalog.findTheme("nope").isEmpty());
	}

	@Test
	void replayingTheScriptsLeavesTheSameCatalog() {
		ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("schema.sql"),
				new ClassPathResource("data.sql"));
		populator.setSqlScriptEncoding("UTF-8");

		populator.execute(dataSource); // what a second start of the server does

		assertEquals(3, count("difficulty"));
		assertEquals(4, count("theme"));
		assertEquals(28, count("question"));
		assertEquals(112, count("question_choice"));
	}

	private long count(String table) {
		return jdbcClient.sql("select count(*) from " + table).query(Long.class).single();
	}

}
