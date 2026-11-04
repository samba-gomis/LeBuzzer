package com.lebuzzer.backend.repository;

import java.util.List;

import org.springframework.data.repository.Repository;

import com.lebuzzer.backend.entity.ThemeEntity;

/** Read-only on purpose: {@code Repository} exposes no write method, only the ones declared here. */
public interface ThemeRepository extends Repository<ThemeEntity, String> {

	List<ThemeEntity> findAllByOrderByPositionAsc();

}
