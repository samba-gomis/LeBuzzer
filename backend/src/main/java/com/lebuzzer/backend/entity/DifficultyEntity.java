package com.lebuzzer.backend.entity;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A row of the {@code difficulty} table: the scoring of one level. Changing the points or the time
 * of a level means changing one line of data.sql.
 */
@Entity
@Table(name = "difficulty")
@Immutable
public class DifficultyEntity {

	@Id
	private String code;

	private String label;

	/** Maximum points for a right answer (see {@code ScoreCalculator}). */
	private int points;

	@Column(name = "duration_ms")
	private int durationMs;

	/** Required by JPA. */
	protected DifficultyEntity() {
	}

	public DifficultyEntity(String code, String label, int points, int durationMs) {
		this.code = code;
		this.label = label;
		this.points = points;
		this.durationMs = durationMs;
	}

	public String getCode() {
		return code;
	}

	public String getLabel() {
		return label;
	}

	public int getPoints() {
		return points;
	}

	public int getDurationMs() {
		return durationMs;
	}

}
