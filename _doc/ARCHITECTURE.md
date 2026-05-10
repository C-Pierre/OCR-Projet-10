# Décisions d'architecture — PoC Tchat YCYW

Ce document explique les choix techniques du PoC et leur lien avec l'architecture cible de la plateforme YCYW.

---

## ADR-001 — WebSocket + STOMP plutôt que HTTP polling

**Contexte** : Le tchat requiert une communication bidirectionnelle en temps réel.

**Options évaluées** :
- HTTP long-polling : le client interroge le serveur régulièrement → latence, surcharge serveur
- Server-Sent Events (SSE) : unidirectionnel (serveur → client uniquement)
- WebSocket natif : bidirectionnel, mais sans convention de routage
- **WebSocket + STOMP** : bidirectionnel + protocole de messaging structuré

**Décision** : WebSocket + STOMP via SockJS.

**Justification** :
- STOMP permet de définir des destinations (`/topic/`, `/app/`) qui correspondent naturellement aux concepts métier (salles, utilisateurs)
- SockJS assure un fallback HTTP pour les environnements restrictifs
- L'intégration Spring Boot (`@MessageMapping`, `@SendTo`) est native et bien documentée
- **En production** : remplacement du broker en mémoire par RabbitMQ (protocole STOMP supporté nativement) sans modification du code Angular

---

## ADR-002 — Angular Signals plutôt qu'Observable/BehaviorSubject pour l'état UI

**Contexte** : Gestion de l'état du composant (messages, connexion, frappe).

**Options évaluées** :
- RxJS `BehaviorSubject` + `AsyncPipe` : pattern historique Angular
- NgRx Store : adapté aux grandes applications, surcoût pour une PoC
- **Angular Signals** : primitive réactive native Angular 17+

**Décision** : Signals pour l'état synchrone, RxJS Subject uniquement pour le stream de messages WebSocket.

**Justification** :
- Signals élimine la nécessité de `async pipe` et de `ChangeDetectorRef` pour l'état local
- `computed()` remplace des pipes complexes ou des méthodes recalculées à chaque cycle
- `effect()` gère proprement les effets de bord (scroll automatique)
- RxJS reste utilisé pour le stream de messages car il offre les opérateurs `debounceTime`, `filter` nécessaires au throttling de l'indicateur de frappe

---

## ADR-003 — Broker en mémoire (PoC) → RabbitMQ (production)

**Contexte** : Le broker STOMP Spring gère les abonnements et la diffusion des messages.

**PoC** : `enableSimpleBroker("/topic")` — broker intégré à Spring, en mémoire.

**Limitation** : si l'application est déployée sur plusieurs instances (scalabilité horizontale), les messages ne sont pas partagés entre instances.

**Production** : remplacement par le connecteur STOMP de RabbitMQ (`spring-boot-starter-reactor-rabbitmq`). La configuration change dans `WebSocketConfig.java` uniquement :

```java
// PoC (actuel)
registry.enableSimpleBroker("/topic");

// Production
registry.enableStompBrokerRelay("/topic")
        .setRelayHost("rabbitmq")
        .setRelayPort(61613);
```

Le code Angular (`websocket.service.ts`) ne change pas.

---

## ADR-004 — Persistance en mémoire (PoC) → PostgreSQL (production)

**PoC** : `ChatRoomService` stocke les messages dans une `ConcurrentHashMap`.

**Limitations** :
- Perte des messages au redémarrage
- Pas de requêtes sur l'historique
- Pas de liaison avec les entités `User` et `Booking`

**Production** : `ChatRoomService` sera remplacé par un `ChatMessageRepository` JPA :
```java
@Entity
public class ChatMessage {
    @Id private UUID id;
    @ManyToOne private User sender;
    @ManyToOne private Booking booking;
    private String content;
    private Instant timestamp;
}
```

---

## ADR-005 — Authentification hors périmètre PoC → Spring Authorization Server en production

**Contexte** : La PoC utilise un pseudonyme libre, sans authentification.

**Production** : le `username` sera remplacé par le `userId` extrait du JWT émis par Spring Authorization Server. Le `roomId` correspondra à l'`id` de la réservation, accessible uniquement aux parties prenantes de cette réservation (client + employé d'agence).

Côté Angular, le `WebSocketService` enrichira les headers STOMP avec le token JWT :
```typescript
this.stompClient = new Client({
  connectHeaders: {
    Authorization: `Bearer ${this.authService.getToken()}`,
  },
  // ...
});
```

Côté Spring Boot, un `ChannelInterceptor` validera le JWT avant d'autoriser la connexion WebSocket :
```java
@Override
public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
    if (StompCommand.CONNECT.equals(accessor.getCommand())) {
        String token = accessor.getFirstNativeHeader("Authorization");
        // validation JWT via Spring Security Resource Server
    }
    return message;
}
```

**Pourquoi Spring Authorization Server et non Keycloak ?**  
Spring Authorization Server est un projet officiel de l'écosystème Spring, intégré directement au projet Spring Boot sans service externe à déployer ni opérer. Il implémente les standards OAuth2/OIDC de façon stricte, ce qui rend une migration ultérieure vers Keycloak non-breaking : seuls l'`issuer` et le `jwks-uri` changent dans la configuration Spring Security Resource Server. Keycloak reste pertinent si le projet évolue vers du SSO multi-applications, une fédération d'identité (LDAP/Active Directory) ou une console d'administration graphique.

---

## ADR-006 — Séparation des responsabilités : ChatController, ChatRoomService, ChatBroadcastService

**Contexte** : La première version du `ChatController` construisait les objets `ChatMessage` directement dans chaque handler et appelait `SimpMessagingTemplate` partout.

**Problèmes identifiés** :
- Duplication du pattern `ChatMessage.builder()...timestamp(Instant.now()).build()` dans chaque méthode
- Le controller connaissait le détail de la destination WebSocket (`/topic/chat/{roomId}`)
- Toute modification de la logique de construction ou de routage nécessitait de toucher au controller

**Décision** : extraction en deux services distincts.

- `ChatRoomService` absorbe la construction de tous les `ChatMessage` (CHAT, JOIN, LEAVE, TYPING). Les méthodes retournent directement le message construit plutôt que `void`, le controller n'a plus qu'à broadcaster le résultat.
- `ChatBroadcastService` encapsule `SimpMessagingTemplate` et la résolution de la destination. Point d'évolution unique si la stratégie de routage change.

**Résultat** : chaque handler du controller se réduit à deux lignes — déléguer au service, broadcaster.

```java
// Avant
chatRoomService.userJoined(roomId, message.getSender());
ChatMessage notification = ChatMessage.builder()
  .type(MessageType.JOIN)
  ...
  .build();
messagingTemplate.convertAndSend("/topic/chat/" + roomId, notification);

// Après
broadcastService.broadcast(chatRoomService.userJoined(roomId, message.getSender()));
```

---

## ADR-007 — Configuration externalisée via variables d'environnement

**Contexte** : Les valeurs de configuration (CORS, datasource, port) étaient soit hardcodées dans les fichiers Java, soit dupliquées entre le `docker-compose.yml` et `application.yaml`.

**Décision** : toute valeur susceptible de changer entre environnements est externalisée.

**Principes appliqués** :
- `application.yaml` déclare les propriétés avec des valeurs par défaut pour le développement local : `${MA_VARIABLE:valeur_par_defaut}`
- Le `.env` porte les valeurs réelles pour Docker Compose, lu automatiquement par Docker via `env_file`
- Le `docker-compose.yml` ne contient aucune valeur hardcodée ; les variables d'environnement propres à Docker (comme `SPRING_DATASOURCE_URL` qui contient le nom du service interne `postgres`) sont définies dans le compose et surchargent `application.yaml`
- Les propriétés natives Spring Boot (`SPRING_DATASOURCE_URL`, `SERVER_PORT`...) sont reconnues automatiquement ; les propriétés custom (`cors.allowed-origins`) doivent être déclarées dans `application.yaml` pour que Spring puisse les résoudre

**Propriétés externalisées** :

| Variable | Défaut local | Utilisée dans |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/ycyw` | `application.yaml` |
| `SPRING_DATASOURCE_USERNAME` | `ycyw_user` | `application.yaml` |
| `SPRING_DATASOURCE_PASSWORD` | `ycyw_dev_password` | `application.yaml` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | `application.yaml` → `CorsConfig` |
| `CORS_ALLOWED_METHODS` | `GET,POST,OPTIONS` | `application.yaml` → `CorsConfig` |
| `DEV_USERNAME` | `admin` | `application.yaml` → `SecurityConfig` |
| `DEV_PASSWORD` | `admin` | `application.yaml` → `SecurityConfig` |
| `SERVER_PORT` | `8080` | `application.yaml` |

---

## ADR-008 — Sécurité : profils Spring dev/prod : TODO

**Contexte** : La `SecurityConfig` initiale désactivait toute protection (`anyRequest().permitAll()`) sans JWT ni authentification, ce qui aurait pu se retrouver en production.

**Décision** : séparation explicite par profils Spring.

**Profil `dev`** : authentification HTTP Basic en mémoire (`InMemoryUserDetailsManager`) avec identifiants lus depuis les variables d'environnement. Déclenche la fenêtre d'authentification native du navigateur. `withDefaultPasswordEncoder()` est utilisé en connaissance de cause — déprécié, mais acceptable car le bean est strictement limité au profil dev.

**Profil `docker`** : même configuration que dev, les identifiants sont injectés depuis le `.env` via Docker Compose.

**Profil `prod`** : `SecurityFilterChain` configuré en OAuth2 Resource Server. Chaque requête doit porter un JWT valide signé par Spring Authorization Server. CSRF désactivé de façon justifiée (API stateless, pas de session cookie).

```java
@Bean
@Profile("prod")
public SecurityFilterChain prodFilterChain(HttpSecurity http) throws Exception {
  http
    .authorizeHttpRequests(auth -> auth
        .requestMatchers("/ws/**").permitAll()
        .anyRequest().authenticated()
    )
    .oauth2ResourceServer(oauth2 -> oauth2
        .jwt(jwt -> jwt.issuerUri(issuerUri))
    )
    .csrf(csrf -> csrf.disable());
  return http.build();
}
```