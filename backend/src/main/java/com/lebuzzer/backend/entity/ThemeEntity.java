package com.lebuzzer.backend.entity;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A row of the {@code theme} table. Read-only: the catalogue only changes through data.sql. */
@Entity
@Table(name = "theme")
@Immutable
public class ThemeEntity {

	@Id
	private String id;

	private String name;

	/** Display order on the home page (the first theme is preselected). */
	private int position;

	/** Required by JPA. */
	protected ThemeEntity() {
	}

	public ThemeEntity(String id, String name, int position) {
		this.id = id;
		this.name = name;
		this.position = position;
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public int getPosition() {
		return position;
	}

}
