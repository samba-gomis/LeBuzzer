# Catalogue de questions en base : plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** remplacer le catalogue écrit dans le code par un catalogue PostgreSQL (4 thèmes × 7 questions, barème par difficulté), chargé une fois au démarrage et gardé en mémoire.

**Architecture:** Spring rejoue `schema.sql` puis `data.sql` à chaque démarrage, Hibernate valide les entités, puis `DatabaseQuestionCatalog` lit le catalogue en deux requêtes. `CatalogAssembler`, une fonction pure, le vérifie et le convertit en records `Theme` / `Question`, que le jeu utilise déjà. L'interface `QuestionCatalog` ne change pas, donc `GameService`, les controllers et le frontend non plus.

**Tech Stack:** Spring Boot 4.1.1, Java 21, Spring Data JPA, Hibernate 7.4 (`jakarta.persistence`), PostgreSQL 17, JUnit 5.

**Spec:** [docs/superpowers/specs/2026-11-03-questions-database-design.md](../specs/2026-11-03-questions-database-design.md)

## Global Constraints

- Aucune nouvelle dépendance dans le `pom.xml`. Pas de Lombok dans le nouveau code (le projet utilise des records et des classes écrites à la main).
- Aucun changement dans `GameService`, `ThemeController`, `model/Theme`, `model/Question`, les DTO ni le frontend.
- Barème : `EASY` « Facile » 500 points, 20 000 ms, `sort_order` 1 ; `MEDIUM` « Moyen » 1 000 points, 20 000 ms, `sort_order` 2 ; `HARD` « Difficile » 1 500 points, 25 000 ms, `sort_order` 3.
- Difficulté selon la position dans le thème : 1 et 2 `EASY`, 3 à 5 `MEDIUM`, 6 et 7 `HARD`.
- Thèmes, dans l'ordre (`position`) : 1 `general` « Culture générale », 2 `sciences` « Sciences et technologies », 3 `sport` « Sport », 4 `anime` « Anime (manga) ».
- Identifiant d'une question : `<themeId>-<position>`, par exemple `anime-7`.
- 7 questions par thème, 4 réponses par question ; positions comptées de 1 à 4 dans la base, de 0 à 3 dans le jeu (`correctIndex = correct_position - 1`), conversion uniquement dans `CatalogAssembler`.
- Messages d'erreur, au mot près : `Catalog has no theme`, `Theme <id> has <n> questions, expected 7`, `Question <id> has <n> choices, expected 4`, levés en `IllegalStateException`.
- `application.properties` : `spring.sql.init.mode=always` et `spring.sql.init.encoding=UTF-8`.
- Les textes visibles par les joueurs sont en français ; le code, les commentaires et les messages de commit sont en anglais, comme dans le reste du dépôt. Java : tabulations, javadoc courte.
- **Git** : Claude ne lance aucune commande git qui écrit. À la fin de chaque tâche, il donne les commandes, et l'utilisateur les lance. Un commit par fichier, dans un ordre où chaque commit compile et passe `.\mvnw.cmd -B verify`.
- **Rythme** : une tâche à la fois ; l'utilisateur relit le résultat avant la tâche suivante.

## Review Focus

1. **Apostrophes et accents dans `data.sql`** : `l'Australie` doit s'écrire `l''Australie` en SQL, et les accents doivent arriver intacts même sous Windows. Test (tâche 4) : `keepsTheExistingGeneralKnowledgeQuestions` compare le texte exact de `general-1` et le nom « Culture générale ».
2. **Deuxième démarrage sur une base déjà remplie** : les scripts sont rejoués et ne doivent ni échouer ni dupliquer les lignes. Test (tâche 4) : `replayingTheScriptsLeavesTheSameCatalog`.
3. **Erreur de copier-coller dans les 21 nouvelles questions** (deux réponses identiques, mauvais identifiant) : test (tâche 4) `everyThemeHasSevenQuestionsOfFourChoicesWithTheScoringOfTheirPosition`, qui vérifie 4 réponses distinctes et `id == themeId-position`. L'exactitude des faits reste une relecture humaine (tâche 1, étape 3).
4. **Thème sans aucune question**, qui disparaîtrait de l'accueil sans erreur : test (tâche 3) `refusesThemeWithoutAnyQuestion`.
5. **Compte à rebours de 25 s** sur les questions difficiles, côté navigateur : vérification à la main (tâche 5, étape 4).

---

### Task 1: Les scripts SQL et leur exécution au démarrage

**Files:**
- Create: `backend/src/main/resources/schema.sql`
- Create: `backend/src/main/resources/data.sql`
- Modify: `backend/src/main/resources/application.properties`

**Interfaces:**
- Produces: les tables `theme`, `difficulty`, `question`, `question_choice`, recréées et remplies à chaque démarrage. Les noms et types de colonnes ci-dessous sont ceux que les entités de la tâche 2 valideront.

- [ ] **Step 1: Écrire `schema.sql`**

```sql
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
	duration_ms INTEGER     NOT NULL CHECK (duration_ms > 0),
	sort_order  INTEGER     NOT NULL UNIQUE
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
```

En tête de fichier, un commentaire de deux lignes : le fichier est rejoué à chaque démarrage, et git est la seule source de vérité.

- [ ] **Step 2: Écrire `data.sql`**

Une instruction `INSERT` multi-lignes par table, dans l'ordre `difficulty`, `theme`, `question`, `question_choice`. Les questions et les réponses sont regroupées par thème, sous un commentaire `-- Culture générale`, `-- Sport`…

- **Culture générale** : les 7 questions de `InMemoryQuestionCatalog`, avec les mêmes textes, réponses et identifiants. Les `correct_position` deviennent, de `general-1` à `general-7` : 2, 1, 4, 2, 1, 3, 4.
- **Sciences, Sport, Anime** : 7 questions chacun, rédigées selon ces règles :
  - en français ;
  - une seule bonne réponse, sans ambiguïté, et des faits qui ne changent pas avec le temps (pas de « champion actuel ») ;
  - des questions de plus en plus difficiles, de la position 1 à la position 7 ;
  - la bonne réponse répartie entre les positions 1 à 4 ;
  - pour Anime (manga), des séries très connues (One Piece, Naruto, Dragon Ball, L'Attaque des Titans, Death Note…), avec des détails plus précis aux positions 6 et 7.
- Chaque apostrophe est doublée (`l''Australie`).

- [ ] **Step 3: Faire relire les 21 nouvelles questions**

Montrer à l'utilisateur, dans le chat, un tableau par thème (question, 4 réponses, bonne réponse en gras, difficulté). Appliquer ses corrections. Attendre son accord avant l'étape suivante.

- [ ] **Step 4: Activer les scripts dans `application.properties`**

Ajouter, sous le bloc PostgreSQL et avant `ddl-auto` :

```properties
# The question catalogue lives in schema.sql and data.sql, replayed at every start:
# git is the only source of truth, a change made by hand in the database is lost.
spring.sql.init.mode=always
spring.sql.init.encoding=UTF-8
```

Adapter le commentaire de `ddl-auto` : Hibernate vérifie les tables créées par ces scripts.

- [ ] **Step 5: Vérifier**

Run (depuis `backend/`) : `.\mvnw.cmd -B verify`
Expected: `BUILD SUCCESS`. Les scripts tournent au démarrage du contexte des tests, et le jeu lit encore `InMemoryQuestionCatalog`.

Puis, dans pgAdmin (Query Tool sur la base `lebuzzer`) :

```sql
SELECT (SELECT count(*) FROM difficulty) AS difficulties, (SELECT count(*) FROM theme) AS themes,
       (SELECT count(*) FROM question) AS questions, (SELECT count(*) FROM question_choice) AS choices;
```

Expected: `3 | 4 | 28 | 112`.

- [ ] **Step 6: Commits (lancés par l'utilisateur)**

```powershell
git add backend/src/main/resources/schema.sql
git commit -m 'feat(backend): add the schema of the question catalogue' -m 'Four tables: theme, difficulty (the scoring), question and question_choice. The script drops and recreates them, so every start begins from a clean catalogue.'
git add backend/src/main/resources/data.sql
git commit -m 'feat(backend): add the 4 themes and 28 questions of the catalogue' -m 'Culture generale keeps the 7 former questions. Easy questions are worth 500 points, medium 1000, hard 1500 with 25 seconds to answer.'
git add backend/src/main/resources/application.properties
git commit -m 'feat(backend): replay the catalogue scripts at every start'
```

---

### Task 2: Les entités et les repositories en lecture seule

**Files:**
- Create: `backend/src/main/java/com/lebuzzer/backend/entity/ThemeEntity.java`
- Create: `backend/src/main/java/com/lebuzzer/backend/entity/DifficultyEntity.java`
- Create: `backend/src/main/java/com/lebuzzer/backend/entity/QuestionEntity.java`
- Create: `backend/src/main/java/com/lebuzzer/backend/repository/ThemeRepository.java`
- Create: `backend/src/main/java/com/lebuzzer/backend/repository/QuestionRepository.java`

**Interfaces:**
- Consumes: les tables de la tâche 1.
- Produces:
  - `ThemeEntity(String id, String name, int position)` : getters `getId()`, `getName()`, `getPosition()` ;
  - `DifficultyEntity(String code, String label, int points, int durationMs, int sortOrder)` : getters `getCode()`, `getLabel()`, `getPoints()`, `getDurationMs()`, `getSortOrder()` ;
  - `QuestionEntity(String id, ThemeEntity theme, int position, DifficultyEntity difficulty, String text, int correctPosition, List<String> choices)` : getters `getId()`, `getTheme()`, `getPosition()`, `getDifficulty()`, `getText()`, `getCorrectPosition()`, `getChoices()` ;
  - `ThemeRepository.findAllByOrderByPositionAsc(): List<ThemeEntity>` ;
  - `QuestionRepository.findAllWithDetails(): List<QuestionEntity>`.

- [ ] **Step 1: Écrire les 3 entités**

- Chaque entité est annotée `@Entity`, `@Table(name = "…")`, et `@org.hibernate.annotations.Immutable` (le catalogue n'est jamais modifié par l'application).
- Chacune a un constructeur `protected` sans argument (exigé par JPA) et le constructeur public ci-dessus, utilisé par les tests de la tâche 3. Pas de setters.
- Les colonnes en deux mots sont nommées avec `@Column(name = "duration_ms")`, `"sort_order"`, `"correct_position"`.
- `QuestionEntity` :
  - `theme` et `difficulty` sont en `@ManyToOne(fetch = FetchType.LAZY, optional = false)`, avec `@JoinColumn(name = "theme_id")` et `@JoinColumn(name = "difficulty_code")` ;
  - `choices` :

```java
@ElementCollection
@CollectionTable(name = "question_choice", joinColumns = @JoinColumn(name = "question_id"))
@OrderColumn(name = "position")
@ListIndexBase(1)
@Column(name = "text", nullable = false)
private List<String> choices = new ArrayList<>();
```

Javadoc de la classe : les positions 1..4 de la base sont gardées telles quelles ici ; c'est `CatalogAssembler` qui les convertit.

- [ ] **Step 2: Écrire les 2 repositories**

Ils étendent `org.springframework.data.repository.Repository<…, String>` (et non `JpaRepository`), pour n'exposer aucune écriture.

```java
@Query("""
		select q from QuestionEntity q
		join fetch q.theme
		join fetch q.difficulty
		left join fetch q.choices
		order by q.theme.position, q.position""")
List<QuestionEntity> findAllWithDetails();
```

`left join fetch` est voulu : une question sans réponse doit quand même arriver jusqu'à `CatalogAssembler`, qui la refusera. Javadoc : une seule requête pour toutes les questions, tout est chargé, donc rien de paresseux après l'appel.

- [ ] **Step 3: Vérifier que les entités correspondent aux tables**

Run : `.\mvnw.cmd -B verify`
Expected: `BUILD SUCCESS`. `BackendApplicationTests.contextLoads` démarre Hibernate en `ddl-auto=validate` : une colonne mal nommée ou mal typée ferait échouer le démarrage avec `Schema-validation: …`.

- [ ] **Step 4: Commits (lancés par l'utilisateur)**

```powershell
git add backend/src/main/java/com/lebuzzer/backend/entity/ThemeEntity.java
git commit -m 'feat(backend): map the theme table'
git add backend/src/main/java/com/lebuzzer/backend/entity/DifficultyEntity.java
git commit -m 'feat(backend): map the difficulty table'
git add backend/src/main/java/com/lebuzzer/backend/entity/QuestionEntity.java
git commit -m 'feat(backend): map the question table and its choices'
git add backend/src/main/java/com/lebuzzer/backend/repository/ThemeRepository.java
git commit -m 'feat(backend): read the themes in display order'
git add backend/src/main/java/com/lebuzzer/backend/repository/QuestionRepository.java
git commit -m 'feat(backend): read all the questions in one query'
```

---

### Task 3: `CatalogAssembler`, la vérification et la conversion

**Files:**
- Create: `backend/src/main/java/com/lebuzzer/backend/service/CatalogAssembler.java`
- Test: `backend/src/test/java/com/lebuzzer/backend/service/CatalogAssemblerTest.java`

**Interfaces:**
- Consumes: les constructeurs et getters des entités (tâche 2) ; les records `model/Theme` et `model/Question` (inchangés).
- Produces: `CatalogAssembler.assemble(List<ThemeEntity> themes, List<QuestionEntity> questions): List<Theme>` (static), et les constantes `QUESTIONS_PER_THEME = 7`, `CHOICES_PER_QUESTION = 4`.

- [ ] **Step 1: Écrire les tests**

Helpers du test : `HARD = new DifficultyEntity("HARD", "Difficile", 1500, 25_000, 3)`, `EASY` (500, 20 000 ms, 1) ; `sevenQuestions(ThemeEntity theme)` construit 7 questions valides `theme-1` à `theme-7` (choix `"A".."D"`, `correctPosition` 1).

| Test | Données | Assertions |
|---|---|---|
| `assemblesThemesAndQuestionsInPositionOrder` | thèmes `b` (position 2) puis `a` (position 1) ; questions des deux thèmes mélangées | ids des thèmes `["a", "b"]` ; ids des questions de `a` : `["a-1", …, "a-7"]` |
| `convertsTheCorrectPositionAndAppliesTheDifficulty` | une question `HARD`, `correctPosition` 4, choix `["W","X","Y","Z"]` | `correctIndex() == 3`, `points() == 1500`, `durationMs() == 25_000`, `choices() == ["W","X","Y","Z"]` ; une question `correctPosition` 1 donne `correctIndex() == 0` |
| `refusesAnEmptyCatalog` | aucun thème | `IllegalStateException`, message `Catalog has no theme` |
| `refusesThemeWithoutAnyQuestion` | thème `anime` sans question, à côté d'un thème complet | message `Theme anime has 0 questions, expected 7` |
| `refusesThemeWithoutSevenQuestions` | thème `anime`, 6 questions | message `Theme anime has 6 questions, expected 7` |
| `refusesQuestionWithoutFourChoices` | thème `sport` complet dont `sport-3` n'a que 3 choix | message `Question sport-3 has 3 choices, expected 4` |

- [ ] **Step 2: Lancer les tests pour les voir échouer**

Run : `.\mvnw.cmd -B test -Dtest=CatalogAssemblerTest`
Expected: échec de compilation, `cannot find symbol … CatalogAssembler`.

- [ ] **Step 3: Écrire `CatalogAssembler`**

- C'est une classe `final`, avec un constructeur privé, sur le modèle de `ScoreCalculator`.
- `assemble` :
  - trie les thèmes par `position` ;
  - regroupe les questions par `getTheme().getId()`, et les trie par `position` dans chaque thème ;
  - applique les vérifications dans l'ordre des messages des Global Constraints ;
  - construit `new Question(id, text, List.copyOf(choices), correctPosition - 1, difficulty.getPoints(), difficulty.getDurationMs())`, puis `new Theme(id, name, questions)` ;
  - renvoie `List.copyOf(…)`.
- Javadoc : ce que l'assembleur vérifie, et pourquoi en Java (ces deux règles ne s'écrivent pas simplement en SQL).

- [ ] **Step 4: Lancer les tests pour les voir passer**

Run : `.\mvnw.cmd -B test -Dtest=CatalogAssemblerTest`
Expected: `Tests run: 6, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commits (lancés par l'utilisateur)**

```powershell
git add backend/src/main/java/com/lebuzzer/backend/service/CatalogAssembler.java
git commit -m 'feat(backend): check and convert the catalogue read from the database' -m 'A theme must have 7 questions and a question 4 choices, otherwise the catalogue is refused with a clear message. Positions 1..4 of the database become the 0-based index used by the game.'
git add backend/src/test/java/com/lebuzzer/backend/service/CatalogAssemblerTest.java
git commit -m 'test(backend): cover the checks of the catalogue'
```

---

### Task 4: `DatabaseQuestionCatalog` remplace le catalogue en mémoire

**Files:**
- Create: `backend/src/main/java/com/lebuzzer/backend/service/DatabaseQuestionCatalog.java`
- Delete: `backend/src/main/java/com/lebuzzer/backend/service/InMemoryQuestionCatalog.java`
- Modify: `backend/src/main/java/com/lebuzzer/backend/service/QuestionCatalog.java` (javadoc seulement)
- Test: `backend/src/test/java/com/lebuzzer/backend/service/DatabaseQuestionCatalogTest.java`

**Interfaces:**
- Consumes: `ThemeRepository.findAllByOrderByPositionAsc()`, `QuestionRepository.findAllWithDetails()` (tâche 2), `CatalogAssembler.assemble(...)` (tâche 3).
- Produces: le seul bean `QuestionCatalog` de l'application.

- [ ] **Step 1: Écrire le test sur la vraie base**

`@SpringBootTest`, avec `@Autowired QuestionCatalog catalog`, `DataSource dataSource` et `JdbcClient jdbcClient`.

| Test | Assertions |
|---|---|
| `loadsFourThemesInDisplayOrder` | ids `["general", "sciences", "sport", "anime"]` ; noms `["Culture générale", "Sciences et technologies", "Sport", "Anime (manga)"]` |
| `everyThemeHasSevenQuestionsOfFourChoicesWithTheScoringOfTheirPosition` | pour chaque thème : ids `themeId-1` … `themeId-7` ; chaque question a 4 réponses **distinctes** ; points `[500, 500, 1000, 1000, 1000, 1500, 1500]` ; durées `[20000, 20000, 20000, 20000, 20000, 25000, 25000]` |
| `keepsTheExistingGeneralKnowledgeQuestions` | `general-1` : texte `Quelle est la capitale de l'Australie ?`, réponses `["Sydney", "Canberra", "Melbourne", "Perth"]`, `choices().get(correctIndex())` vaut `Canberra` |
| `findsAThemeByIdAndNothingForAnUnknownId` | `findTheme("anime")` présent, avec 7 questions ; `findTheme("nope")` vide |
| `replayingTheScriptsLeavesTheSameCatalog` | rejoue `schema.sql` puis `data.sql` avec `ResourceDatabasePopulator` (`setSqlScriptEncoding("UTF-8")`) sur `dataSource`, sans exception ; puis `jdbcClient` compte 3 difficultés, 4 thèmes, 28 questions et 112 réponses |

- [ ] **Step 2: Lancer le test pour le voir échouer**

Run : `.\mvnw.cmd -B test -Dtest=DatabaseQuestionCatalogTest`
Expected: FAIL. `loadsFourThemesInDisplayOrder` ne trouve que `["general"]`, parce que le bean est encore `InMemoryQuestionCatalog`.

- [ ] **Step 3: Écrire `DatabaseQuestionCatalog` et supprimer `InMemoryQuestionCatalog`**

- C'est un `@Component` qui implémente `QuestionCatalog`.
- Son constructeur reçoit `ThemeRepository` et `QuestionRepository`. Il appelle `CatalogAssembler.assemble(themeRepository.findAllByOrderByPositionAsc(), questionRepository.findAllWithDetails())`, garde le résultat dans un champ `final List<Theme> themes`, puis écrit dans les logs `Question catalogue loaded: 4 themes, 28 questions` (les nombres sont calculés).
- `findTheme` cherche dans cette liste, comme le faisait l'ancienne classe.
- Javadoc : le catalogue est lu une fois au démarrage et ne fait plus aucune requête ensuite ; un refus de `CatalogAssembler` arrête le démarrage.
- Supprimer `InMemoryQuestionCatalog.java` dans le même commit : avec deux beans `QuestionCatalog`, Spring ne saurait pas lequel injecter.
- Dans `QuestionCatalog`, remplacer « today an in-memory implementation, later the PostgreSQL one » par la description de l'implémentation actuelle.

- [ ] **Step 4: Lancer toute la suite**

Run : `.\mvnw.cmd -B verify`
Expected: `BUILD SUCCESS`. `DatabaseQuestionCatalogTest` passe 5 tests. `GameServiceTest`, `BackendApplicationTests` et `StompSecurityIntegrationTest` passent sans modification : ce dernier crée une partie sur `general`, qui existe toujours.

- [ ] **Step 5: Commits (lancés par l'utilisateur)**

```powershell
git add backend/src/main/java/com/lebuzzer/backend/service/DatabaseQuestionCatalog.java
git rm backend/src/main/java/com/lebuzzer/backend/service/InMemoryQuestionCatalog.java
git commit -m 'feat(backend): read the question catalogue from PostgreSQL' -m 'The catalogue is loaded once at startup and kept in memory: no SQL query is made during a game. It replaces the temporary in-memory catalogue.'
git add backend/src/main/java/com/lebuzzer/backend/service/QuestionCatalog.java
git commit -m 'docs(backend): describe the database implementation of the catalogue'
git add backend/src/test/java/com/lebuzzer/backend/service/DatabaseQuestionCatalogTest.java
git commit -m 'test(backend): cover the catalogue loaded from the database'
```

---

### Task 5: Documentation et vérification dans le navigateur

**Files:**
- Modify: `README.md`
- Modify: `Buzz Runner/preparation-oral.md` (hors dépôt, aucun commit)

- [ ] **Step 1: Mettre à jour `README.md`**

- § 2.1, lignes 29 à 31 : la base contient le catalogue, recréé à chaque démarrage à partir de `schema.sql` et `data.sql` ; modifier une question, c'est modifier `data.sql` puis redémarrer ; une modification faite dans pgAdmin est perdue.
- Ligne 93 : remplacer « Les joueurs ont 20 secondes » par le barème (20 s, ou 25 s pour les questions difficiles ; 500, 1 000 ou 1 500 points).
- Tableau des problèmes courants (vers la ligne 246) : ajouter une ligne pour `Theme … has … questions, expected 7` ou `Question … has … choices, expected 4`, causés par une erreur dans `data.sql`.
- Arborescence : ajouter `entity/` (les tables du catalogue), `repository/` (leur lecture), et `resources/` (`schema.sql`, `data.sql`).

- [ ] **Step 2: Mettre à jour `preparation-oral.md`**

Retirer ou corriger tout passage qui présente la base comme vide. Ajouter la question « Pourquoi charger le catalogue en mémoire au démarrage ? », avec la réponse de la section 2 de la spec (pas de requête pendant le jeu, les parties continuent si la base tombe, une donnée cassée bloque le démarrage au lieu d'une partie).

- [ ] **Step 3: Lancer le projet**

Run (deux terminaux) : `.\mvnw.cmd spring-boot:run` dans `backend/`, puis `npm run dev` dans `frontend/`.
Expected: le log du backend contient `Question catalogue loaded: 4 themes, 28 questions`.

- [ ] **Step 4: Vérifier à la main dans le navigateur**

1. L'accueil affiche 4 thèmes, dans l'ordre, Culture générale présélectionné, chacun avec « 7 questions ».
2. Une partie complète sur Anime (manga), avec 1 animateur et 2 joueurs : les questions 1 et 2 valent 500 points, les questions 3 à 5 valent 1 000 points, et les questions 6 et 7 valent 1 500 points avec un décompte qui part de 25 s ; le classement final est cohérent.

- [ ] **Step 5: Commit (lancé par l'utilisateur)**

```powershell
git add README.md
git commit -m 'docs: explain the question catalogue stored in PostgreSQL'
git status
git push -u origin feat/questions-database
```

`git status` doit afficher `nothing to commit, working tree clean`. Ensuite : PR vers `main`, CI verte, fusion.
