# YCYW Chat PoC

> **Preuve de concept** — Fonctionnalité de tchat en temps réel  
> Projet : Your Car Your Way · Stack : Spring Boot 4 / Java 25 + Angular 21

---

## Objectif de ce PoC

Ce dépôt démontre la **faisabilité technique du tchat en temps réel** dans l'architecture cible de la plateforme Your Car Your Way.

Il ne s'agit **pas** d'une application complète. Le périmètre est volontairement restreint :

| Ce qui est inclus ✅ | Ce qui est hors périmètre ❌ |
|---|---|
| Connexion WebSocket (STOMP sur SockJS) | Authentification complète (Spring Authorization Server) |
| Envoi et réception de messages en temps réel | Base de données persistante |
| Salles de tchat par identifiant de réservation | Fonctionnalités de réservation |
| Indicateur de frappe (*user is typing…*) | Interface graphique finalisée |
| Historique de session (en mémoire) | Déploiement en production |

---

## Architecture démontrée

```
┌─────────────────┐        WebSocket / STOMP        ┌──────────────────────┐
│  Angular 21     │ ◄──────────────────────────────► │  Spring Boot 4       │
│  (Frontend)     │   SockJS fallback HTTP           │  (Backend)           │
│                 │                                  │                      │
│  Port 4200      │   /ws  ──► endpoint              │  Port 8080           │
│                 │   /app ──► @MessageMapping        │                      │
│                 │   /topic ◄── @SendTo             │  In-memory message   │
└─────────────────┘                                  │  broker              │
                                                     └──────────────────────┘
```

**Pourquoi WebSocket + STOMP ?**  
Le tchat exige une communication **bidirectionnelle en temps réel**. Le HTTP classique (requête/réponse) ne suffit pas : le serveur ne peut pas envoyer un message au client sans que celui-ci l'ait demandé. WebSocket maintient une connexion ouverte permanente. STOMP est un protocole de messaging léger qui s'ajoute au-dessus de WebSocket pour structurer les échanges (topics, queues, abonnements).

---

## Prérequis

Avant de commencer, assure-toi d'avoir installé :

| Outil | Version minimale | Vérification |
|---|---|---|
| Java (JDK) | 25 | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| Node.js | 20+ | `node -v` |
| npm | 10+ | `npm -v` |
| Angular CLI | 21+ | `ng version` |
| Docker + Docker Compose | 24+ | `docker -v` |
| Git | 2.40+ | `git -v` |

---

## Configuration de l'environnement

Le projet utilise un fichier `.env` pour externaliser toutes les valeurs de configuration. Spring Boot ne lit pas ce fichier nativement — c'est Docker Compose qui l'injecte comme variables d'environnement.

### Créer le fichier `.env`

À la racine du projet, crée un fichier `.env` à partir du modèle fourni :

```bash
cp .env.example .env
```

Le contenu par défaut fonctionne tel quel pour le développement local :

```env
# PostgreSQL
POSTGRES_DB=ycyw
POSTGRES_USER=ycyw_user
POSTGRES_PASSWORD=ycyw_dev_password

# Spring datasource
SPRING_DATASOURCE_USERNAME=ycyw_user
SPRING_DATASOURCE_PASSWORD=ycyw_dev_password

# CORS
CORS_ALLOWED_ORIGINS=http://localhost:4200
CORS_ALLOWED_METHODS=GET,POST,OPTIONS

# Authentification dev (fenêtre navigateur)
DEV_USERNAME=admin
DEV_PASSWORD=admin

# Serveur
SERVER_PORT=8080
```

> Le fichier `.env` est dans le `.gitignore` et ne doit **jamais** être commité. Il contient des secrets.

---

## Démarrage rapide (5 minutes)

### Option A — Avec Docker Compose (recommandé)

C'est la façon la plus simple. Docker s'occupe de tout.

```bash
# 1. Cloner le dépôt
git clone https://github.com/<ton-org>/ycyw-chat-poc.git
cd ycyw-chat-poc

# 2. Créer le fichier d'environnement
cp .env.example .env

# 3. Lancer tous les services
docker compose up --build

# 4. Ouvrir le tchat
# → http://localhost:4200
```

**Authentification** : le navigateur affiche une fenêtre de connexion. Utilise les identifiants définis dans ton `.env` (`DEV_USERNAME` / `DEV_PASSWORD`, par défaut `admin` / `admin`).

Pour arrêter :
```bash
docker compose down
```

---

### Option B — Lancement manuel (pour le développement)

Lance le backend et le frontend dans deux terminaux séparés.

**Terminal 1 — Backend :**
```bash
cd backend
mvn spring-boot:run
# ✅ Serveur démarré sur http://localhost:8080
```

**Terminal 2 — Frontend :**
```bash
cd frontend
npm install
ng serve
# ✅ Application disponible sur http://localhost:4200
```

En lancement manuel, Spring Boot lit `application.yaml` avec les valeurs par défaut (localhost, port 5432, identifiants dev). Assure-toi qu'une instance PostgreSQL tourne localement, ou lance uniquement la base via Docker :

```bash
docker compose up postgres
```

Ouvre `http://localhost:4200` dans **deux onglets différents** pour simuler deux utilisateurs.

---

## Structure du projet

```
ycyw-chat-poc/
│
├── backend/                        # Spring Boot 4 / Java 25
│   └── src/main/java/com/ycyw/chat/
│       ├── config/
│       │   ├── WebSocketConfig.java     # Configuration du broker STOMP
│       │   ├── CorsConfig.java          # CORS — origines lues depuis .env
│       │   └── SecurityConfig.java      # Sécurité par profil (dev/docker/prod)
│       ├── controller/
│       │   └── ChatController.java      # Points d'entrée WebSocket (@MessageMapping)
│       ├── model/
│       │   ├── ChatMessage.java         # Structure d'un message
│       │   └── MessageType.java         # Types : CHAT, JOIN, LEAVE, TYPING
│       ├── service/
│       │   ├── ChatRoomService.java     # Gestion des salles, historique et construction des messages
│       │   └── ChatBroadcastService.java# Diffusion WebSocket (encapsule SimpMessagingTemplate)
│       └── resources/
│           └── application.yml          # Configuration avec valeurs par défaut
│
├── frontend/                       # Angular 21
│   └── src/app/
│       ├── chat/
│       │   ├── chat.component.ts        # Composant principal du tchat
│       │   ├── chat.component.html      # Template HTML
│       │   ├── chat.component.scss      # Styles
│       │   └── chat.routes.ts           # Routes Angular
│       └── core/
│           └── websocket.service.ts     # Service de connexion WebSocket (STOMP)
│
├── docs/
│   └── architecture-decisions.md   # ADR — pourquoi ces choix techniques
│
├── docker-compose.yml              # Environnement de développement complet
├── .env.example                    # Modèle de configuration (à copier en .env)
├── .env                            # Variables locales — NE PAS COMMITER
└── README.md                       # Ce fichier
```

---

## Tester le tchat

1. Ouvre `http://localhost:4200` dans deux onglets du navigateur
2. Dans l'onglet 1 : entre le pseudonyme `Alice` et rejoins la salle `booking-123`
3. Dans l'onglet 2 : entre le pseudonyme `Bob` et rejoins la même salle `booking-123`
4. Envoie un message depuis Alice → il apparaît instantanément chez Bob ✅
5. Tape un message sans l'envoyer → Bob voit *"Alice est en train d'écrire…"* ✅

---

## API WebSocket — Pour les curieux

Le backend expose les endpoints suivants :

| Type | Chemin | Description |
|---|---|---|
| Connexion | `ws://localhost:8080/ws` | Point d'entrée WebSocket (avec fallback SockJS HTTP) |
| Envoyer un message | `/app/chat/{roomId}/send` | Publie un message dans une salle |
| Rejoindre une salle | `/app/chat/{roomId}/join` | Notifie les autres utilisateurs |
| Quitter une salle | `/app/chat/{roomId}/leave` | Notifie les autres utilisateurs |
| Indiquer la frappe | `/app/chat/{roomId}/typing` | Diffuse l'indicateur de frappe |
| S'abonner aux messages | `/topic/chat/{roomId}` | Reçoit tous les messages d'une salle |

**Exemple de payload (message) :**
```json
{
  "type": "CHAT",
  "content": "Bonjour !",
  "sender": "Alice",
  "roomId": "booking-123",
  "timestamp": "2026-04-24T10:30:00Z"
}
```

Tu peux aussi tester l'API REST de diagnostic :
```bash
# Voir l'historique d'une salle
curl http://localhost:8080/api/chat/booking-123/history

# Voir les membres d'une salle
curl http://localhost:8080/api/chat/booking-123/members

# Voir les salles actives
curl http://localhost:8080/api/chat/rooms
```

---

## Problèmes courants

**`JAVA_HOME` non défini**
```bash
export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
```

**Le port 8080 est déjà utilisé**
```bash
# Trouver le processus
lsof -i :8080
# Changer le port dans le .env
SERVER_PORT=8081
```

**Erreur CORS au démarrage Angular**  
Vérifie que le backend tourne bien sur le port 8080 et que `CORS_ALLOWED_ORIGINS` dans ton `.env` correspond à l'URL du frontend. Le proxy Angular (`frontend/proxy.conf.json`) redirige `/api` et `/ws` vers `http://localhost:8080`.

**`Could not resolve placeholder 'cors.allowed-origins'`**  
Le fichier `.env` n'existe pas ou n'a pas été chargé. En local sans Docker, les valeurs par défaut de `application.yaml` prennent le relais — vérifie que les propriétés `cors.*` sont bien présentes dans le yaml.

**`ng: command not found`**
```bash
npm install -g @angular/cli@21
```

**La fenêtre d'authentification ne s'affiche pas**  
Le profil Spring actif n'est peut-être pas `dev` ou `docker`. Vérifie `SPRING_PROFILES_ACTIVE` dans ton `.env` ou dans la commande de lancement.

---

## Comment contribuer (workflow Git)

```bash
# 1. Crée une branche depuis main
git checkout -b feature/ma-fonctionnalite

# 2. Fais tes modifications, puis commit
git add .
git commit -m "feat: description courte de ce que tu as fait"

# 3. Pousse ta branche
git push origin feature/ma-fonctionnalite

# 4. Ouvre une Pull Request sur GitHub
```

Conventions de commit :
- `feat:` nouvelle fonctionnalité
- `fix:` correction de bug
- `docs:` modification de documentation
- `chore:` maintenance (dépendances, config)

---

## 📚 Ressources pour approfondir

- [Documentation Spring WebSocket](https://docs.spring.io/spring-framework/docs/current/reference/html/web.html#websocket)
- [Spring Authorization Server](https://spring.io/projects/spring-authorization-server)
- [STOMP over WebSocket](https://stomp-js.github.io/stomp-websocket/codo/extra/docs-src/Usage.md.html)
- [Angular Signals](https://angular.dev/guide/signals)
- [Spring Boot 4 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Release-Notes)
- [Décisions d'architecture YCYW](./docs/architecture-decisions.md)