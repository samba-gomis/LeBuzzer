package com.lebuzzer.backend.model;

import java.util.List;

/** A theme of the catalog and its questions, in the order they are played. */
public record Theme(String id, String name, List<Question> questions) {

	public Theme {
		questions = List.copyOf(questions);
		if (questions.isEmpty()) {
			throw new IllegalArgumentException("Theme " + id + " has no question");
		}
	}

}
