# Polling HTTP ou WebSocket : le cas de Buzz.Runner

**Scénario de référence** (imposé par le sujet) : une partie de 10 minutes, 15 joueurs connectés, et pour le
polling une requête toutes les 2 secondes. Notre partie type comporte 7 questions de 20 s, une toutes les 78 s
environ.

## 1. Polling : le calcul de référence

600 s / 2 s = 300 requêtes par client, donc **4 500 requêtes** pour 15 joueurs, et **4 800** avec l'écran
animateur. Chacune est un aller-retour HTTP complet, en-têtes compris : **9 600 messages HTTP**. Le serveur répond
même quand rien n'a changé. Or, dans notre partie, rien ne change pendant les écrans de résultat
(7 × 58,6 s, plus les 10 dernières secondes, soit 420 s sur 600) : pendant **70 % de la partie**, les requêtes
reviendraient vides.

## 2. WebSocket : ce que nous avons mesuré

**Méthode.** Un intercepteur (`MessageCounterInterceptor`) compte chaque message STOMP sur les deux canaux du
serveur, ce que les clients envoient et ce qu'ils reçoivent. Les chiffres sont lus sur `GET /api/metrics/{code}`.
La partie a été jouée le 20/10/2026 contre le vrai backend par 16 clients STOMP (1 animateur et 15 joueurs),
simulés par un script et configurés comme le frontend : heartbeats de 10 s, mêmes abonnements, 90 % de réponses.
Elle a duré 10 minutes.

| Messages STOMP sur 10 min | Client → serveur | Serveur → client | Total |
|---|---:|---:|---:|
| Applicatifs (actions et événements de jeu) | 120 | 1 928 | **2 048** |
| Heartbeats | 927 | 560 | **1 487** |
| Protocole (`CONNECT`, `CONNECTED`, `SUBSCRIBE`) | 64 | 16 | **80** |
| **Total** | **1 111** | **2 504** | **3 615** |

Ne sont pas comptées : la déconnexion finale (64 trames de fermeture, dont une partie interne au serveur) et la
connexion courte de création de partie.

**Ce que révèle le détail par type :**

- **Une diffusion vaut 16 messages**, un par connexion de la salle : 7 `QUESTION_STARTED` donnent 112 messages.
- **`ANSWER_PROGRESS` fait 1 536 messages, soit 80 % des envois du serveur** : chacune des 96 réponses est relayée
  aux 16 connexions, alors que les joueurs ne l'affichent pas. La réserver à l'animateur supprimerait
  1 440 messages, soit 40 % du total. C'est une optimisation que seule la mesure a révélée.
- **Les heartbeats font 41 % des trames.** C'est le prix de la détection des connexions mortes, environ 20 s en
  test (une coupure Wi-Fi ne ferme pas toujours la connexion). Chaque heartbeat n'est qu'un saut de ligne.
- **Les clients n'envoient que 120 messages utiles** : 15 `join`, 96 `answer` et 9 actions de l'animateur.

## 3. Comparaison

| | Polling toutes les 2 s (calcul) | WebSocket (mesuré) |
|---|---|---|
| Échanges sur 10 min | 4 800 requêtes = 9 600 messages HTTP | 3 615 messages STOMP |
| Quand des données circulent | à chaque requête, même sans nouveauté | quand l'état change, plus les heartbeats |
| Poids d'un échange | requête HTTP complète avec ses en-têtes | trame courte sur une connexion déjà ouverte |
| Latence perçue (action de l'animateur → écran du joueur) | attente de 0 à 2 s, **1 s en moyenne**, plus un aller-retour réseau | **11 ms en médiane, 54 ms au 95e centile** (105 mesures en local), plus un trajet serveur → client sur un vrai réseau |
| Équité entre joueurs | jusqu'à 2 s d'écart pour voir la même question, soit 10 % du temps de réponse | même instant pour tous |
| Côté serveur | sans état, facile à répartir | 16 connexions ouvertes, un état en mémoire, une logique de reconnexion |

Les latences WebSocket ont été mesurées sur une seule machine : elles incluent le traitement client, pas le trajet
réseau.

## 4. Quand le polling reste le bon choix

- **Données rares ou statiques** : la liste des thèmes est lue une fois par une simple requête
  (`GET /api/themes`). Une connexion permanente n'apporterait rien.
- **Réseaux filtrés** : en séminaire d'entreprise, un proxy ou un pare-feu peut bloquer les WebSockets ; le
  polling passe partout où le web passe.
- **Hébergement serverless** : des fonctions qui ne vivent que le temps d'une requête ne peuvent pas tenir une
  connexion ouverte 10 minutes.
- **Simplicité et cache** : un serveur sans état se duplique facilement et ses réponses se mettent en cache (CDN).
  Nos parties, elles, vivent dans la mémoire d'un seul serveur.

## 5. Conclusion

Pour un quiz minuté, le WebSocket l'emporte. Il échange moins de messages (3 615 mesurés contre 9 600 messages
HTTP), il n'envoie de données que lorsqu'il se passe quelque chose, et surtout il diffuse **au même instant chez
tous les joueurs**. C'est indispensable quand le score dépend de la rapidité et que le serveur fait autorité sur le
temps. Ce gain a un coût : un serveur avec état, des heartbeats (41 % des trames), et une reconnexion avec backoff
et rattrapage d'état qu'il a fallu concevoir et tester. Le polling reste le bon outil pour les données statiques,
et une solution de repli quand le réseau interdit les WebSockets.
