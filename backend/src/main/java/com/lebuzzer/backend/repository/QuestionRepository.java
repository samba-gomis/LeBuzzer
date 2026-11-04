package com.lebuzzer.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import com.lebuzzer.backend.entity.QuestionEntity;

/** Read-only on purpose: {@code Repository} exposes no write method, only the ones declared here. */
public interface QuestionRepository extends Repository<QuestionEntity, String> {

	/**
	 * Every question with its theme, difficulty and choices, in one query (no query per question).
	 * Everything is fetched, so nothing is loaded lazily after the call. {@code left join fetch}
	 * on purpose: a question without any choice still comes back, and {@code CatalogAssembler}
	 * refuses it.
	 */
	@Query("""
			select q from QuestionEntity q
			join fetch q.theme
			join fetch q.difficulty
			left join fetch q.choices
			order by q.theme.position, q.position""")
	List<QuestionEntity> findAllWithDetails();

}
