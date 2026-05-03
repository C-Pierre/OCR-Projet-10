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
        .setRelayPort(61613); // port STOMP de RabbitMQ
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
// Entité JPA à créer
@Entity
public class ChatMessage {
    @Id private UUID id;
    @ManyToOne private User sender;     // → entité User (modèle de données YCYW)
    @ManyToOne private Booking booking; // → entité Booking
    private String content;
    private Instant timestamp;
}
```

---

## ADR-005 — Authentification hors périmètre PoC

**Contexte** : La PoC utilise un pseudonyme libre, sans authentification.

**Production** : le `username` sera remplacé par le `userId` extrait du JWT Keycloak. Le `roomId` correspondra à l'`id` de la réservation, accessible uniquement aux parties prenantes de cette réservation (client + employé d'agence).

Côté Angular, le `WebSocketService` enrichira les headers STOMP avec le token JWT :
```typescript
this.stompClient = new Client({
  connectHeaders: {
    Authorization: `Bearer ${this.authService.getToken()}`,
  },
  // ...
});
```

Côté Spring Boot, un `ChannelInterceptor` validera le JWT avant d'autoriser la connexion WebSocket.