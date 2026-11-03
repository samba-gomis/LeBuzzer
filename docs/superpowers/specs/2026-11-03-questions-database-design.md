# Catalogue de questions en base PostgreSQL : design

Date : 2026-11-03. Statut : design validé section par section avec l'équipe, spec à relire.

## 1. Objectif

Aujourd'hui, le backend exige une base PostgreSQL `lebuzzer` pour démarrer, mais n'y lit rien : les questions
viennent de `InMemoryQuestionCatalog`, un seul thème de 7 questions écrit dans le code.

Après ce changement :

- la base contient le catalogue : **4 thèmes de 7 questions**, et le **barème par difficulté** ;
- le jeu lit ce catalogue, et la base sert enfin à quelque chose ;
- les parties en cours restent **en mémoire**, comme aujourd'hui : rien ne change dans leur déroulement.

Hors périmètre : enregistrer les parties, les joueurs ou les scores en base ; écrire des questions depuis
l'application ; changer la formule de rapidité.

## 2. Principe

> La base est la source du catalogue, la mémoire sert le jeu.

1. Au démarrage, Spring rejoue `schema.sql` puis `data.sql` sur la base (`spring.sql.init.mode=always`).
2. Hibernate vérifie ensuite que les entités correspondent aux tables (`ddl-auto=validate`, déjà configuré).
3. `DatabaseQuestionCatalog` lit tout le catalogue, le vérifie, le transforme en objets `Theme` et `Question`
   (ceux que le jeu utilise déjà) et le garde en mémoire.
4. Pendant le jeu, **aucune requête SQL n'est faite** : `GET /api/themes` et la création d'une partie lisent
   ces objets.

Conséquences assumées :

- **git est la seule source de vérité** : une question modifiée à la main dans pgAdmin disparaît au redémarrage
  suivant. Modifier une question, c'est modifier `data.sql`, puis redémarrer ;
- la base de l'animateur et celle du binôme ne peuvent pas diverger ;
- si la base tombe pendant une partie, la partie continue ;
- une donnée incohérente **empêche le serveur de démarrer**, avec un message clair, au lieu de faire planter une
  partie en pleine question.

Choix écartés : Flyway (fait pour faire évoluer des données qu'on ne veut pas perdre, ce qui n'est pas le cas
d'un catalogue versionné dans git) et des requêtes SQL écrites à la main sans JPA (il faudrait assembler
soi-même les lignes en thèmes et questions, alors que JPA est déjà dans le projet).

## 3. Schéma

`schema.sql` commence par supprimer les 4 tables (`DROP TABLE IF EXISTS … CASCADE`), puis les recrée : chaque
démarrage repart d'une base propre.

```
theme            (id, name, position)
difficulty       (code, label, points, duration_ms, sort_order)
question         (id, theme_id → theme, position, difficulty_code → difficulty, text, correct_position)
question_choice  (question_id → question, position, text)
```

| Table | Colonne | Règle |
|---|---|---|
| `theme` | `id` | clé primaire, texte court et stable, par exemple `general`, `anime` |
| | `name` | non nul, affiché à l'accueil |
| | `position` | non nul, unique : ordre d'affichage à l'accueil (le premier est présélectionné) |
| `difficulty` | `code` | clé primaire : `EASY`, `MEDIUM`, `HARD` |
| | `label` | non nul : `Facile`, `Moyen`, `Difficile` |
| | `points` | non nul, `> 0` |
| | `duration_ms` | non nul, `> 0` |
| | `sort_order` | non nul, unique : du plus facile au plus difficile |
| `question` | `id` | clé primaire, le thème suivi de la position, par exemple `general-1`, `anime-7` |
| | `theme_id` | non nul, clé étrangère vers `theme` |
| | `position` | non nul, de 1 à 7 ; unique dans un thème (`UNIQUE (theme_id, position)`) |
| | `difficulty_code` | non nul, clé étrangère vers `difficulty` |
| | `text` | non nul |
| | `correct_position` | non nul, de 1 à 4 : la position de la bonne réponse |
| `question_choice` | `question_id` | non nul, clé étrangère vers `question` |
| | `position` | non nul, de 1 à 4 ; clé primaire `(question_id, position)` |
| | `text` | non nul |

Les positions sont comptées **de 1 à 4 dans la base**, comme A, B, C, D, ce qui rend le script plus lisible.
Le jeu les compte de 0 à 3 (`correctIndex`) : la conversion se fait à un seul endroit, dans
`CatalogAssembler` (section 5).

Deux règles ne s'expriment pas simplement en SQL : « exactement 7 questions par thème » et « exactement
4 réponses par question ». Elles sont vérifiées au chargement (section 6).

## 4. Barème et contenu

Le barème est une table : le changer revient à modifier une ligne de `data.sql`.

| Code | Libellé | Points | Durée | Positions dans un thème |
|---|---|---|---|---|
| `EASY` | Facile | 500 | 20 s | 1 et 2 |
| `MEDIUM` | Moyen | 1 000 | 20 s | 3, 4 et 5 |
| `HARD` | Difficile | 1 500 | 25 s | 6 et 7 |

La formule de rapidité ne change pas (`ScoreCalculator`) : une bonne réponse rapporte de 50 % (au dernier
moment) à 100 % (immédiatement) des points de la question, une mauvaise réponse 0.

Les 4 thèmes, dans l'ordre d'affichage :

| Position | `id` | Nom |
|---|---|---|
| 1 | `general` | Culture générale |
| 2 | `sciences` | Sciences et technologies |
| 3 | `sport` | Sport |
| 4 | `anime` | Anime (manga) |

Soit **28 questions**. Celles de Culture générale reprennent les 7 questions actuelles de
`InMemoryQuestionCatalog`, avec les mêmes identifiants, de `general-1` à `general-7`. Les 21 autres sont
rédigées en premier jet par Claude et relues par l'équipe avant le commit de `data.sql`.

## 5. Composants du backend

| Élément | Rôle |
|---|---|
| `resources/schema.sql` | supprime puis crée les 4 tables et leurs contraintes |
| `resources/data.sql` | insère le barème, les 4 thèmes, les 28 questions et leurs 112 réponses |
| `application.properties` | ajoute `spring.sql.init.mode=always` et `spring.sql.init.encoding=UTF-8` (sans cet encodage, les accents de `data.sql` risquent d'être abîmés sous Windows) |
| package `entity` | 3 entités JPA en lecture seule : thème, difficulté, question ; les réponses sont une collection de la question, triée par position |
| package `repository` | les requêtes de lecture du catalogue |
| `service/CatalogAssembler` | fonction pure, sans Spring ni base : reçoit les entités, vérifie les règles de la section 6, convertit les positions 1..4 en index 0..3 et renvoie la liste immuable des `Theme` |
| `service/DatabaseQuestionCatalog` | implémente `QuestionCatalog` : à sa création, lit le catalogue avec le repository, le donne à `CatalogAssembler` et garde le résultat ; ensuite, ne fait que lire cette liste en mémoire |
| `service/InMemoryQuestionCatalog` | **supprimé** |
| `service/QuestionCatalog` | interface inchangée ; seule sa javadoc, qui annonce l'implémentation PostgreSQL, est mise à jour |

Les entités ne quittent jamais le chargement : `GameService`, `ThemeController` et le frontend ne connaissent
que l'interface `QuestionCatalog` et les records `Theme` et `Question`, qui ne changent pas.

**Nombre de requêtes** : le chargement fait deux requêtes, quel que soit le nombre de questions :

1. les thèmes, triés par position ;
2. toutes les questions, avec leur difficulté et leurs réponses (`join fetch`), triées par thème puis par
   position.

Il n'y a donc pas une requête par question. Les thèmes sont lus à part pour qu'un thème sans aucune question
soit détecté, au lieu de disparaître en silence de la liste.

Le catalogue chargé est immuable : plusieurs parties peuvent le lire en même temps sans verrou.

## 6. Erreurs

Toutes les erreurs liées au catalogue se voient **au démarrage**, jamais pendant une partie.

| Cas | Ce qui se passe |
|---|---|
| PostgreSQL éteint, base ou utilisateur absent | le serveur ne démarre pas (comme aujourd'hui) ; le README indique quoi vérifier |
| Erreur SQL dans `schema.sql` ou `data.sql` (contrainte violée, faute de frappe) | le serveur ne démarre pas ; le message de PostgreSQL désigne la ligne fautive |
| Une table ne correspond pas à son entité | Hibernate bloque le démarrage (`ddl-auto=validate`) |
| Aucun thème | `CatalogAssembler` refuse : `Catalog has no theme` |
| Un thème qui n'a pas exactement 7 questions | `CatalogAssembler` refuse, par exemple : `Theme anime has 6 questions, expected 7` |
| Une question qui n'a pas exactement 4 réponses | `CatalogAssembler` refuse, par exemple : `Question sport-3 has 3 choices, expected 4` |
| Une bonne réponse qui ne désigne aucune réponse | rendue impossible par la contrainte `1..4` de la base et la règle des 4 réponses ; le record `Question` le vérifie aussi |
| Un thème inconnu demandé à la création d'une partie | inchangé : `THEME_NOT_FOUND` |

Un refus de `CatalogAssembler` lève une `IllegalStateException` : la création du bean échoue, donc Spring
s'arrête et affiche le message.

## 7. Frontend

**Aucun changement de code.**

- L'accueil lit déjà `GET /api/themes` et affiche « nom (n questions) » : les 4 thèmes apparaissent seuls, dans
  l'ordre de la base, et Culture générale reste présélectionné.
- La carte de question affiche déjà les points de la question.
- Le compte à rebours suit la durée et l'heure de fin envoyées par le serveur : les 25 s des questions
  difficiles fonctionnent sans modification.

## 8. Tests

| Test | Base ? | Ce qu'il vérifie |
|---|---|---|
| `CatalogAssemblerTest` (nouveau) | non | un catalogue correct est converti (positions 1..4 vers index 0..3, ordre des thèmes et des questions) ; chaque cas de refus de la section 6 lève une erreur avec le bon message |
| `DatabaseQuestionCatalogTest` (nouveau) | oui, la vraie base, comme dans la CI | après le chargement de `schema.sql` et `data.sql` : 4 thèmes dans l'ordre de la section 4, 7 questions par thème, 4 réponses par question, 500 points aux positions 1 et 2, 1 000 aux positions 3 à 5, 1 500 aux positions 6 et 7, 25 s pour les questions difficiles |
| `GameServiceTest` | non | inchangé : il garde son faux catalogue |
| `BackendApplicationTests`, `StompSecurityIntegrationTest` | oui | inchangés : ils démarrent maintenant avec le vrai catalogue, et le thème `general` existe toujours |

La CI ne change pas : elle démarre déjà un PostgreSQL 17 avec les mêmes identifiants.

**Vérification à la main** (dans le navigateur, comme pour les phases précédentes) :

1. l'accueil affiche les 4 thèmes, Culture générale présélectionné ;
2. une partie complète sur Anime (manga), avec 1 animateur et 2 joueurs : 7 questions, des questions faciles à
   500 points, puis moyennes à 1 000, puis difficiles à 1 500 avec un décompte de 25 s, et un classement
   final cohérent.

## 9. Documentation

- `README.md` :
  - § 2.1 : la base contient désormais le catalogue, recréé à chaque démarrage ; modifier une question, c'est
    modifier `data.sql` puis redémarrer ;
  - déroulement d'une partie : remplacer « Les joueurs ont 20 secondes » par le barème (20 s, ou 25 s pour les
    questions difficiles, et les points par niveau) ;
  - arborescence : ajouter `schema.sql`, `data.sql` et les packages `entity` et `repository`.
- `Buzz Runner/preparation-oral.md` (hors dépôt) : la base n'est plus vide ; ajouter une réponse à la question
  « pourquoi le catalogue est-il chargé en mémoire au démarrage ? ».

## 10. Critères de réussite

- Le backend démarre sur une base `lebuzzer` vide (créée selon le § 2.1 du README), sans aucune action manuelle
  de plus : les tables et les 28 questions apparaissent seules.
- `.\mvnw.cmd -B verify` passe en local, et la CI est verte.
- La vérification à la main de la section 8 passe.
- `InMemoryQuestionCatalog` n'existe plus, et `GameService` comme le frontend n'ont pas changé.
