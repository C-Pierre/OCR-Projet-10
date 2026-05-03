// Configuration de l'environnement de développement
//
// Ce fichier est utilisé quand tu lances `ng serve` (développement local).
// En production, Angular le remplace automatiquement par environment.production.ts
// grâce au mécanisme de "file replacement" dans angular.json.
//
// COMMENT L'UTILISER DANS UN SERVICE :
//   import { environment } from '../../environments/environment';
//   const url = environment.wsUrl; // → 'ws://localhost:8080/ws' en dev
//                                  // → 'wss://api.ycyw.com/ws' en prod

export const environment = {
    production: false,

    // URL du backend Spring Boot (WebSocket)
    // En développement : le proxy Angular (proxy.conf.json) redirige /ws → localhost:8080
    // On utilise un chemin relatif pour que le proxy fonctionne
    wsUrl: '/ws',

    // URL de base de l'API REST (diagnostic)
    apiUrl: '/api',

    // Activer les logs STOMP en console (pratique pour déboguer en dev)
    stompDebug: true,
};