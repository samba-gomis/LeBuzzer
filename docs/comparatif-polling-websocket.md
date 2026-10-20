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
Deux parties de 10 minutes ont été jouées le 20/10/2026 contre le vrai backend par 16 clients STOMP (1 animateur
et 15 joueurs), simulés par un script et configurés comme le frontend (heartbeats de 10 s, 90 % de réponses).

| Messages STOMP sur 10 min | Version 1 : progression diffusée à la salle | Version 2 : progression à l'animateur seul |
|---|---:|---:|
| Applicatifs (actions et événements de jeu) | 2 048 | **605** |
| Heartbeats | 1 487 | **1 576** |
| Protocole (`CONNECT`, `CONNECTED`, `SUBSCRIBE`) | 80 | **81** |
| **Total** | **3 615** | **2 262** |

Dans la version 2 (la version livrée), ce sont 1 111 messages des clients vers le serveur, et 1 151 du serveur
vers les clients. Ne sont pas comptées : la déconnexion finale (64 trames de fermeture, dont une partie interne au
serveur) et la connexion courte de création de partie.

**Ce que la mesure a révélé :**

- **Une diffusion vaut 16 messages**, un par connexion de la salle : 7 `QUESTION_STARTED` donnent 112 messages.
- **En version 1, `ANSWER_PROGRESS` faisait 1 536 messages, soit 80 % des messages applicatifs envoyés par le
  serveur** : chacune des
  96 réponses était relayée aux 16 connexions, alors que les joueurs ne l'affichent pas. Nous l'envoyons
  désormais à l'animateur seul (`/user/queue/progress`). Résultat : **−1 353 messages, soit −37 %**, et les
  joueurs n'en reçoivent plus aucune.
- **Les heartbeats ont légèrement augmenté** (+89), parce que le serveur n'en envoie à une connexion que lorsqu'il
  n'a rien d'autre à lui envoyer. Ils représentent maintenant **70 % des trames**. C'est le prix de la détection
  des connexions mortes (environ 20 s en test, une coupure Wi-Fi ne fermant pas toujours la connexion), mais
  chacun n'est qu'un saut de ligne.
- **Les clients n'envoient qu'environ 120 messages utiles** : 15 `join`, 95 ou 96 `answer` selon la partie, et
  9 actions de l'animateur.

## 3. Comparaison

| | Polling toutes les 2 s (calcul) | WebSocket (mesuré, version 2) |
|---|---|---|
| Échanges sur 10 min | 4 800 requêtes = 9 600 messages HTTP | 2 262 messages STOMP |
| Quand des données circulent | à chaque requête, même sans nouveauté | quand l'état change, plus les heartbeats |
| Poids d'un échange | requête HTTP complète avec ses en-têtes | trame courte sur une connexion déjà ouverte |
| Latence perçue (action de l'animateur → écran du joueur) | attente de 0 à 2 s, **1 s en moyenne**, plus un aller-retour réseau | **14 ms en médiane, 22 ms au 95e centile** (105 mesures en local), plus un trajet serveur → client sur un vrai réseau |
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

Pour un quiz minuté, le WebSocket l'emporte : **quatre fois moins de messages** (2 262 mesurés contre 9 600
messages HTTP), des données envoyées seulement quand il se passe quelque chose, et surtout une diffusion **au même
instant chez tous les joueurs**, indispensable quand le score dépend de la rapidité. La mesure a aussi servi à
**améliorer** l'implémentation (−37 % de messages). Le coût : un serveur avec état, des heartbeats (70 % des
trames), et une reconnexion avec backoff et rattrapage d'état à concevoir et tester. Le polling reste le bon outil
pour les données statiques, et une solution de repli quand le réseau interdit les WebSockets.
