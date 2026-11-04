-- The question catalogue, replayed at every start (spring.sql.init.mode=always) before data.sql.
-- git is the only source of truth: a change made by hand in the database is lost at the next start.

DROP TABLE IF EXISTS question_choice, question, difficulty, theme CASCADE;

CREATE TABLE theme (
	id       VARCHAR(50)  PRIMARY KEY,
	name     VARCHAR(100) NOT NULL,
	position INTEGER      NOT NULL UNIQUE CHECK (position > 0)
);

CREATE TABLE difficulty (
	code        VARCHAR(20) PRIMARY KEY,
	label       VARCHAR(50) NOT NULL,
	points      INTEGER     NOT NULL CHECK (points > 0),
	duration_ms INTEGER     NOT NULL CHECK (duration_ms > 0)
);

CREATE TABLE question (
	id               VARCHAR(50)  PRIMARY KEY,
	theme_id         VARCHAR(50)  NOT NULL REFERENCES theme (id),
	position         INTEGER      NOT NULL CHECK (position BETWEEN 1 AND 7),
	difficulty_code  VARCHAR(20)  NOT NULL REFERENCES difficulty (code),
	text             VARCHAR(500) NOT NULL,
	correct_position INTEGER      NOT NULL CHECK (correct_position BETWEEN 1 AND 4),
	UNIQUE (theme_id, position)
);

CREATE TABLE question_choice (
	question_id VARCHAR(50)  NOT NULL REFERENCES question (id),
	position    INTEGER      NOT NULL CHECK (position BETWEEN 1 AND 4),
	text        VARCHAR(200) NOT NULL,
	PRIMARY KEY (question_id, position)
);
