// Configuration de l'environnement de production
// Ce fichier remplace environment.ts lors d'un build production (`ng build`).
// Les valeurs ici pointent vers l'infrastructure YCYW réelle.

export const environment = {
    production: true,

    // En production : URL complète WSS (WebSocket sécurisé, port 443)
    // À remplacer par le vrai domaine avant le déploiement
    wsUrl: 'wss://api.ycyw.com/ws',

    apiUrl: 'https://api.ycyw.com/api',

    // Logs STOMP désactivés en production
    stompDebug: false,
};