-- The question catalogue, replayed at every start right after schema.sql.
-- Positions count from 1 (choices 1..4 = A..D); the game counts from 0, see CatalogAssembler.
-- In SQL, an apostrophe inside a text is written twice: 'l''Australie'.

INSERT INTO difficulty (code, label, points, duration_ms, sort_order) VALUES
	('EASY',   'Facile',    500,  20000, 1),
	('MEDIUM', 'Moyen',     1000, 20000, 2),
	('HARD',   'Difficile', 1500, 25000, 3);

INSERT INTO theme (id, name, position) VALUES
	('general',  'Culture générale',         1),
	('sciences', 'Sciences et technologies', 2),
	('sport',    'Sport',                    3),
	('anime',    'Anime (manga)',            4);

INSERT INTO question (id, theme_id, position, difficulty_code, text, correct_position) VALUES
	-- Culture générale
	('general-1', 'general', 1, 'EASY',   'Quelle est la capitale de l''Australie ?', 2),
	('general-2', 'general', 2, 'EASY',   'Combien de côtés a un hexagone ?', 1),
	('general-3', 'general', 3, 'MEDIUM', 'Quelle planète est surnommée la planète rouge ?', 4),
	('general-4', 'general', 4, 'MEDIUM', 'Qui a peint La Joconde ?', 2),
	('general-5', 'general', 5, 'MEDIUM', 'En quelle année a eu lieu la prise de la Bastille ?', 1),
	('general-6', 'general', 6, 'HARD',   'Quel est le symbole chimique de l''or ?', 3),
	('general-7', 'general', 7, 'HARD',   'Quel est le plus long fleuve de France ?', 4),
	-- Sciences et technologies
	('sciences-1', 'sciences', 1, 'EASY',   'Quelle est la formule chimique de l''eau ?', 2),
	('sciences-2', 'sciences', 2, 'EASY',   'Combien de planètes compte le système solaire ?', 3),
	('sciences-3', 'sciences', 3, 'MEDIUM', 'Quel est l''os le plus long du corps humain ?', 1),
	('sciences-4', 'sciences', 4, 'MEDIUM', 'Quel langage sert à décrire la structure d''une page web ?', 4),
	('sciences-5', 'sciences', 5, 'MEDIUM', 'Quel scientifique a formulé la théorie de la relativité ?', 2),
	('sciences-6', 'sciences', 6, 'HARD',   'Quelle est, environ, la vitesse de la lumière dans le vide ?', 3),
	('sciences-7', 'sciences', 7, 'HARD',   'Quel est l''élément chimique le plus abondant dans l''Univers ?', 4),
	-- Sport
	('sport-1', 'sport', 1, 'EASY',   'Combien de joueurs une équipe de football aligne-t-elle sur le terrain ?', 3),
	('sport-2', 'sport', 2, 'EASY',   'Dans quel sport utilise-t-on un volant ?', 2),
	('sport-3', 'sport', 3, 'MEDIUM', 'Tous les combien ont lieu les Jeux olympiques d''été ?', 1),
	('sport-4', 'sport', 4, 'MEDIUM', 'Quel pays a remporté la Coupe du monde de football 2018 ?', 4),
	('sport-5', 'sport', 5, 'MEDIUM', 'Quelle est la distance officielle d''un marathon ?', 2),
	('sport-6', 'sport', 6, 'HARD',   'Dans quelle ville se sont tenus les premiers Jeux olympiques modernes, en 1896 ?', 3),
	('sport-7', 'sport', 7, 'HARD',   'Combien de titres de champion du monde de Formule 1 Michael Schumacher a-t-il remportés ?', 1),
	-- Anime (manga)
	('anime-1', 'anime', 1, 'EASY',   'Dans One Piece, quel est le rêve de Monkey D. Luffy ?', 2),
	('anime-2', 'anime', 2, 'EASY',   'Dans Dragon Ball, combien de boules de cristal faut-il réunir pour invoquer Shenron ?', 3),
	('anime-3', 'anime', 3, 'MEDIUM', 'Dans L''Attaque des Titans, comment s''appelle le personnage principal ?', 4),
	('anime-4', 'anime', 4, 'MEDIUM', 'Dans Naruto, comment s''appelle le démon renard à neuf queues scellé en Naruto ?', 1),
	('anime-5', 'anime', 5, 'MEDIUM', 'Dans Death Note, quel dieu de la mort laisse tomber le cahier dans le monde des humains ?', 2),
	('anime-6', 'anime', 6, 'HARD',   'Qui est l''auteur du manga One Piece ?', 3),
	('anime-7', 'anime', 7, 'HARD',   'En quelle année le manga Dragon Ball a-t-il commencé à paraître au Japon ?', 1);

INSERT INTO question_choice (question_id, position, text) VALUES
	-- Culture générale
	('general-1', 1, 'Sydney'), ('general-1', 2, 'Canberra'), ('general-1', 3, 'Melbourne'), ('general-1', 4, 'Perth'),
	('general-2', 1, '6'), ('general-2', 2, '5'), ('general-2', 3, '8'), ('general-2', 4, '7'),
	('general-3', 1, 'Vénus'), ('general-3', 2, 'Jupiter'), ('general-3', 3, 'Saturne'), ('general-3', 4, 'Mars'),
	('general-4', 1, 'Michel-Ange'), ('general-4', 2, 'Léonard de Vinci'), ('general-4', 3, 'Raphaël'), ('general-4', 4, 'Botticelli'),
	('general-5', 1, '1789'), ('general-5', 2, '1792'), ('general-5', 3, '1776'), ('general-5', 4, '1815'),
	('general-6', 1, 'Ag'), ('general-6', 2, 'Fe'), ('general-6', 3, 'Au'), ('general-6', 4, 'Or'),
	('general-7', 1, 'La Seine'), ('general-7', 2, 'Le Rhône'), ('general-7', 3, 'La Garonne'), ('general-7', 4, 'La Loire'),
	-- Sciences et technologies
	('sciences-1', 1, 'CO2'), ('sciences-1', 2, 'H2O'), ('sciences-1', 3, 'O2'), ('sciences-1', 4, 'NaCl'),
	('sciences-2', 1, '7'), ('sciences-2', 2, '9'), ('sciences-2', 3, '8'), ('sciences-2', 4, '10'),
	('sciences-3', 1, 'Le fémur'), ('sciences-3', 2, 'Le tibia'), ('sciences-3', 3, 'L''humérus'), ('sciences-3', 4, 'Le radius'),
	('sciences-4', 1, 'Python'), ('sciences-4', 2, 'SQL'), ('sciences-4', 3, 'Java'), ('sciences-4', 4, 'HTML'),
	('sciences-5', 1, 'Isaac Newton'), ('sciences-5', 2, 'Albert Einstein'), ('sciences-5', 3, 'Galilée'), ('sciences-5', 4, 'Marie Curie'),
	('sciences-6', 1, '30 000 km/s'), ('sciences-6', 2, '150 000 km/s'), ('sciences-6', 3, '300 000 km/s'), ('sciences-6', 4, '3 000 000 km/s'),
	('sciences-7', 1, 'L''oxygène'), ('sciences-7', 2, 'Le carbone'), ('sciences-7', 3, 'L''hélium'), ('sciences-7', 4, 'L''hydrogène'),
	-- Sport
	('sport-1', 1, '9'), ('sport-1', 2, '10'), ('sport-1', 3, '11'), ('sport-1', 4, '12'),
	('sport-2', 1, 'Le tennis'), ('sport-2', 2, 'Le badminton'), ('sport-2', 3, 'Le squash'), ('sport-2', 4, 'Le tennis de table'),
	('sport-3', 1, 'Tous les 4 ans'), ('sport-3', 2, 'Tous les 2 ans'), ('sport-3', 3, 'Tous les 5 ans'), ('sport-3', 4, 'Tous les 6 ans'),
	('sport-4', 1, 'La Croatie'), ('sport-4', 2, 'Le Brésil'), ('sport-4', 3, 'L''Allemagne'), ('sport-4', 4, 'La France'),
	('sport-5', 1, '40 km'), ('sport-5', 2, '42,195 km'), ('sport-5', 3, '41,5 km'), ('sport-5', 4, '45 km'),
	('sport-6', 1, 'Paris'), ('sport-6', 2, 'Londres'), ('sport-6', 3, 'Athènes'), ('sport-6', 4, 'Rome'),
	('sport-7', 1, '7'), ('sport-7', 2, '5'), ('sport-7', 3, '6'), ('sport-7', 4, '8'),
	-- Anime (manga)
	('anime-1', 1, 'Devenir amiral de la Marine'), ('anime-1', 2, 'Devenir le roi des pirates'), ('anime-1', 3, 'Devenir le plus grand épéiste du monde'), ('anime-1', 4, 'Devenir Hokage'),
	('anime-2', 1, '5'), ('anime-2', 2, '9'), ('anime-2', 3, '7'), ('anime-2', 4, '8'),
	('anime-3', 1, 'Levi Ackerman'), ('anime-3', 2, 'Armin Arlert'), ('anime-3', 3, 'Reiner Braun'), ('anime-3', 4, 'Eren Jäger'),
	('anime-4', 1, 'Kurama'), ('anime-4', 2, 'Shukaku'), ('anime-4', 3, 'Gyûki'), ('anime-4', 4, 'Matatabi'),
	('anime-5', 1, 'Rem'), ('anime-5', 2, 'Ryûk'), ('anime-5', 3, 'Sidoh'), ('anime-5', 4, 'Gelus'),
	('anime-6', 1, 'Masashi Kishimoto'), ('anime-6', 2, 'Akira Toriyama'), ('anime-6', 3, 'Eiichirô Oda'), ('anime-6', 4, 'Hajime Isayama'),
	('anime-7', 1, '1984'), ('anime-7', 2, '1980'), ('anime-7', 3, '1989'), ('anime-7', 4, '1992');
