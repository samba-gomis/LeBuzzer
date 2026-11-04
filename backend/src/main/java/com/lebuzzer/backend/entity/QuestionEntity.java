package com.lebuzzer.backend.entity;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.ListIndexBase;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

/**
 * A row of the {@code question} table, with its choices from {@code question_choice}.
 * Positions are kept as stored, counted from 1 (choices 1..4 = A..D): {@code CatalogAssembler}
 * converts them to the 0-based index used by the game.
 */
@Entity
@Table(name = "question")
@Immutable
public class QuestionEntity {

	@Id
	private String id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "theme_id")
	private ThemeEntity theme;

	/** Order of the question in its theme, from 1 to 7. */
	private int position;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "difficulty_code")
	private DifficultyEntity difficulty;

	private String text;

	/** Position of the right choice, from 1 to 4. */
	@Column(name = "correct_position")
	private int correctPosition;

	/** The choices in their order: the list index is the {@code position} column, counted from 1. */
	@ElementCollection
	@CollectionTable(name = "question_choice", joinColumns = @JoinColumn(name = "question_id"))
	@OrderColumn(name = "position")
	@ListIndexBase(1)
	@Column(name = "text", nullable = false)
	private List<String> choices = new ArrayList<>();

	/** Required by JPA. */
	protected QuestionEntity() {
	}

	public QuestionEntity(String id, ThemeEntity theme, int position, DifficultyEntity difficulty, String text,
			int correctPosition, List<String> choices) {
		this.id = id;
		this.theme = theme;
		this.position = position;
		this.difficulty = difficulty;
		this.text = text;
		this.correctPosition = correctPosition;
		this.choices = new ArrayList<>(choices);
	}

	public String getId() {
		return id;
	}

	public ThemeEntity getTheme() {
		return theme;
	}

	public int getPosition() {
		return position;
	}

	public DifficultyEntity getDifficulty() {
		return difficulty;
	}

	public String getText() {
		return text;
	}

	public int getCorrectPosition() {
		return correctPosition;
	}

	public List<String> getChoices() {
		return choices;
	}

}
