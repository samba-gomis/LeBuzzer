# LeBuzzer — Buzz.Runner

Quiz en direct façon Kahoot. Un animateur crée une partie, qui produit un code à 5 lettres ; les joueurs la
rejoignent depuis leur téléphone avec ce code et un pseudo. Les questions, les réponses et le classement
circulent en temps réel par WebSocket (STOMP).

- **Backend** : Spring Boot 4, Java 21, WebSocket + STOMP (broker simple en mémoire), PostgreSQL.
- **Frontend** : React 19, TypeScript strict, Vite, `@stomp/stompjs`, `react-router`.

---

## 1. Prérequis

| Outil | Version | Vérifier |
|---|---|---|
| Java (JDK) | 21 | `java -version` |
| Node.js | 22 ou plus récent | `node -v` |
| PostgreSQL | 15 ou plus récent, **ou** Docker | `psql --version` / `docker --version` |
| Git | — | `git --version` |

Maven n'a pas besoin d'être installé : le projet contient son propre lanceur (`mvnw`).

## 2. Lancer le projet

Toutes les commandes partent de la racine du dépôt. Sous macOS ou Linux, remplacez `.\mvnw.cmd` par `./mvnw`.

### 2.1 La base de données (une seule fois)

Le backend se connecte à une base `lebuzzer` avec l'utilisateur `lebuzzer` (mot de passe `lebuzzer`).
Les questions sont pour l'instant fournies par un catalogue en mémoire : la base est configurée et prête à
recevoir le catalogue, mais elle doit exister pour que le backend démarre.

**Option A — PostgreSQL installé sur la machine.** Dans pgAdmin (Query Tool sur la base `postgres`) ou dans
`psql -U postgres`, exécutez ces deux commandes **une par une** (`CREATE DATABASE` refuse d'être lancé avec
une autre commande) :

```sql
CREATE ROLE lebuzzer WITH LOGIN PASSWORD 'lebuzzer';
CREATE DATABASE lebuzzer OWNER lebuzzer;
```

`OWNER lebuzzer` est important : depuis PostgreSQL 15, seul le propriétaire d'une base peut y créer des tables.

**Option B — Docker.** Le fichier [compose.yaml](compose.yaml) démarre un PostgreSQL 17 avec les mêmes identifiants :

```bash
docker compose up -d      # démarrer
docker compose down       # arrêter (ajouter -v pour effacer les données)
```

Le port 5432 doit être libre : si un PostgreSQL local tourne déjà, utilisez l'option A.

Pour utiliser d'autres identifiants sans modifier le code, définissez `SPRING_DATASOURCE_URL`,
`SPRING_DATASOURCE_USERNAME` et `SPRING_DATASOURCE_PASSWORD` avant de lancer le backend.

### 2.2 Le backend

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Il est prêt quand le terminal affiche `Started BackendApplication`. Il écoute sur `http://localhost:8080`.

### 2.3 Le frontend (dans un second terminal)

```powershell
cd frontend
npm install        # la première fois seulement
npm run dev
```

Ouvrez **http://localhost:5173**. Vite affiche aussi une adresse `Network: http://192.168.x.x:5173` : c'est
celle à utiliser depuis un téléphone connecté au même Wi-Fi. Si Windows demande d'autoriser Node.js sur les
réseaux privés, acceptez.

### 2.4 Les ports

| Port | Service | Contenu |
|---|---|---|
| 5173 | Vite (frontend) | l'application ; il relaie `/api` et `/ws` vers le backend |
| 8080 | Spring Boot (backend) | `GET /api/themes` et le point d'entrée WebSocket `/ws` |
| 5432 | PostgreSQL | la base `lebuzzer` |

Le navigateur ne parle qu'à Vite : le frontend n'a donc aucune adresse de backend en dur, et il n'y a pas de CORS à
configurer en développement.

## 3. Jouer une partie

1. **Animateur** : sur la page d'accueil, choisissez un thème puis **Créer la partie**. Le code s'affiche en grand.
2. **Joueurs** : sur la page d'accueil (ou depuis un téléphone), saisissez le code et un pseudo, puis **Rejoindre**.
   Ils apparaissent en direct dans la liste de l'animateur.
3. **Animateur** : **Lancer la première question**. Les joueurs ont 20 secondes ; l'animateur voit la progression
   des réponses et peut **Clore la question** avant la fin.
4. À la clôture, chaque joueur voit s'il a bon, ses points et son rang ; l'animateur voit le classement et passe à la
   **Question suivante** ou termine la partie.

Une bonne réponse rapporte entre 50 % (au dernier moment) et 100 % (immédiatement) des points de la question ;
une mauvaise réponse rapporte 0. Le barème par difficulté est provisoire.

Pour tester seul sur un ordinateur, ouvrez **un onglet par joueur** : chaque onglet garde sa propre identité
(`sessionStorage`). Deux onglets du même navigateur sont donc deux joueurs différents.

## 4. Vérifications automatiques

```powershell
cd backend
.\mvnw.cmd -B verify       # compile et lance les 30 tests (PostgreSQL doit tourner)

cd ..\frontend
npm run lint               # TypeScript strict + ESLint, le moindre avertissement est bloquant
npm run build
```

Ce sont les mêmes commandes que la CI GitHub ([.github/workflows/ci.yml](.github/workflows/ci.yml)), lancée sur
chaque pull request vers `main`.

Les tests du backend ([GameServiceTest](backend/src/test/java/com/lebuzzer/backend/service/GameServiceTest.java))
couvrent notamment : code unique, pseudo déjà pris, double réponse, réponse hors délai, réponse sur une question
close, calcul du score, isolation de deux parties, mauvais `hostToken`, reconnexion avec le `playerId`, clôture
automatique et manuelle.

## 5. Procédure de test des cas limites

Les quatre difficultés du sujet se testent à la main avec un navigateur de bureau (Chrome ou Edge pour les
DevTools). **Préparation** : backend et frontend lancés, un onglet animateur qui a créé une partie, et deux ou trois
onglets joueurs qui l'ont rejointe.

### 5.1 Le joueur qui arrive en cours de partie

1. L'animateur lance une question.
2. Attendez 5 à 10 secondes, puis ouvrez un nouvel onglet et rejoignez la partie avec un nouveau pseudo.

**Attendu** : le nouveau joueur voit immédiatement la question en cours, avec **le même temps restant que
l'animateur**, et il peut répondre (la progression de l'animateur augmente). À la clôture, il apparaît dans le
classement.

*Pourquoi* : à l'arrivée, le serveur envoie un **snapshot**, c'est-à-dire l'état complet de la partie (question
ouverte, heure de fin, classement), et pas seulement les événements suivants.

### 5.2 La coupure réseau et la reconnexion

1. Dans l'onglet d'un joueur, ouvrez les DevTools (F12), onglet **Console** visible.
2. Onglet **Network** (Réseau) : passez de *No throttling* à **Offline**.
3. Pendant la coupure, l'animateur passe à la question suivante.
4. Attendez environ 30 secondes, puis repassez en *No throttling*.

**Attendu** :

- après environ 20 secondes, le badge du joueur passe de « Connecté » à **« Reconnexion… »** ;
- la console affiche les tentatives, de plus en plus espacées :
  `[gameSocket] connection lost, retry #1 in ~1000 ms`, puis `#2 in ~2000 ms`, `#3 in ~4000 ms`,
  `#4 in ~8000 ms`… (plafond de 30 s, avec ±20 % d'aléatoire) ;
- après environ 30 secondes, l'animateur voit un joueur connecté de moins ;
- une fois le réseau revenu, le badge repasse à « Connecté » en quelques secondes, et l'écran affiche **la
  nouvelle question** : l'état a été rattrapé, le score est conservé, et le joueur peut répondre.

*Pourquoi environ 20 secondes* : le mode Offline du navigateur ne ferme pas la connexion WebSocket déjà ouverte ;
elle devient simplement muette. Ce sont les **heartbeats** STOMP (un signal toutes les 10 s dans les deux sens) qui
révèlent la coupure : le client abandonne la connexion après deux signaux manqués, le serveur après trois
intervalles. Sur un vrai téléphone qui perd le Wi-Fi, c'est le même mécanisme.

**Variante rapide** : appuyez sur **F5** pendant une partie. Le joueur revient instantanément avec le même pseudo,
le même score et l'écran correspondant à l'état de la partie.

### 5.3 L'autorité du serveur sur le temps

Le serveur décide seul : il calcule l'heure de fin de chaque question, la clôt automatiquement, et refuse toute
réponse arrivée après. Le navigateur se contente d'afficher le décompte.

1. **Clôture automatique** : lancez une question et ne répondez pas. À 0, la question se ferme d'elle-même chez
   tout le monde et le classement s'affiche.
2. **Clôture manuelle** : lancez une question et cliquez sur **Clore la question** avant la fin. La question se
   ferme tout de suite, et la clôture automatique prévue ne se déclenche pas une seconde fois.
3. **Horloge du téléphone décalée** : **entre deux questions** (écran de résultat ou d'attente), exécutez dans la
   console d'un onglet joueur
   ```js
   Date.now = ((realNow) => () => realNow() + 30000)(Date.now)
   ```
   L'horloge de cet onglet avance désormais de 30 secondes. Le badge peut afficher « Reconnexion… » une seconde :
   les heartbeats mesurent le temps avec la même horloge et croient à 30 s de silence. L'animateur lance ensuite la
   question suivante.
   **Attendu** : le décompte du joueur reste identique à celui de l'animateur, et il peut répondre normalement.
   Le client recalcule le décalage entre son horloge et celle du serveur à chaque message
   (`décalage = serverNow - Date.now()`), et l'heure de fin vient du serveur : l'horloge du téléphone ne compte pas.
   Si l'horloge change **pendant** une question, le décompte de ce joueur est faux jusqu'au message suivant du
   serveur, soit une dizaine de secondes au plus, le temps que les heartbeats provoquent une reconnexion. Le
   serveur, lui, n'est jamais trompé.
4. **Réponse en retard** : l'interface verrouille les boutons à 0, et le serveur refuse de toute façon une réponse
   reçue après l'heure de fin, même si la clôture automatique n'a pas encore tourné. Ce cas est couvert par le test
   `anAnswerAfterTheDeadline_isRefused_evenBeforeTheTimerRuns`.

### 5.4 Les cas limites de joueur

| Cas | Manipulation | Attendu |
|---|---|---|
| Double réponse | pendant une question, double-cliquez sur une réponse | une seule réponse comptée (l'animateur voit « 1 / … »), les boutons se verrouillent ; le serveur refuserait de toute façon la seconde (`ALREADY_ANSWERED`) |
| Onglet fermé | fermez l'onglet d'un joueur pendant une partie | il disparaît de la liste des connectés en moins d'une seconde, la partie continue, et il garde sa place au classement |
| Deux parties en parallèle | créez une deuxième partie dans un autre onglet animateur, avec ses propres joueurs, puis lancez des questions dans chacune | aucune question, aucun joueur ni aucun classement d'une partie n'apparaît dans l'autre (chaque partie a sa salle : `/topic/game/{code}`) |
| Pseudo déjà pris | rejoignez une partie avec un pseudo existant (même avec d'autres majuscules) | refus : « Ce pseudo est déjà pris dans cette partie », sans créer de joueur |

### 5.5 Résultats de référence

Mesures relevées lors d'une exécution de cette procédure (1 animateur, jusqu'à 3 joueurs, navigateur automatisé),
pour comparer avec vos propres essais :

| Test | Résultat mesuré |
|---|---|
| Retardataire arrivé 6 s après le début | même temps restant que l'animateur (13 s des deux côtés), réponse acceptée |
| Détection de la coupure par le client | 21 s |
| Détection de la coupure par le serveur | 29 s |
| Délais de reconnexion affichés | 1,2 s, 2,1 s, 3,9 s, 6,5 s |
| Reconnexion après le retour du réseau | 2,9 s, nouvelle question affichée, score conservé |
| Horloge décalée de +30 s (avant la question) | décompte identique à celui de l'animateur (19 s / 19 s, puis 17 s / 17 s), réponse acceptée |
| Horloge décalée de +30 s (pendant une question) | décompte faux pendant 10 s, puis reconnexion automatique et décompte rétabli |
| Onglet fermé | retiré des joueurs connectés en 61 ms |
| Double-clic | 1 réponse comptée, points ajoutés une seule fois |
| Deux parties en parallèle | aucune fuite, dans les deux sens |

## 6. Dépannage

| Symptôme | Cause probable |
|---|---|
| Le backend ne démarre pas : `password authentication failed for user "lebuzzer"` | l'utilisateur `lebuzzer` n'existe pas ou n'a pas ce mot de passe (§ 2.1), ou une variable `SPRING_DATASOURCE_PASSWORD` est définie |
| `database "lebuzzer" does not exist` | la base n'a pas été créée (§ 2.1) |
| « Impossible de charger les thèmes » sur l'accueil | le backend n'est pas démarré, ou pas sur le port 8080 |
| Un téléphone n'arrive pas à se connecter | il n'est pas sur le même Wi-Fi, utilise `localhost` au lieu de l'adresse `Network:` de Vite, ou le pare-feu bloque Node.js |
| « Partie indisponible » (animateur) ou « Impossible de rejoindre » (joueur) après un redémarrage du backend | les parties vivent en mémoire : un redémarrage les efface, il faut en créer une nouvelle |

## 7. Structure du dépôt

```
LeBuzzer/
├── .github/workflows/ci.yml   CI : backend (verify) et frontend (lint + build)
├── backend/                   Spring Boot
│   └── src/main/java/com/lebuzzer/backend/
│       ├── config/            WebSocket/STOMP, identité anonyme par connexion, horloge et minuteur
│       ├── controller/        GameController (STOMP), ThemeController (REST)
│       ├── service/           GameService : toutes les règles du jeu
│       ├── model/             l'état d'une partie en mémoire
│       ├── dto/               le contrat des messages échangés
│       └── event/             détection des déconnexions
├── frontend/                  Vite + React
│   └── src/
│       ├── services/          client STOMP et reconnexion, mémoire de l'onglet, appels REST
│       ├── hooks/             liaison avec React (useGameSocket, useCountdown…)
│       ├── types/             miroir TypeScript du contrat, et validation des messages reçus
│       ├── components/        éléments d'interface
│       └── pages/             accueil, animateur, joueur
├── docs/                      note comparative polling / WebSocket
└── compose.yaml               PostgreSQL pour le développement (optionnel)
```
