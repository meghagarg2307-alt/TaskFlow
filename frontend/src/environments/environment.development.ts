/**
 * Dev environment. The Angular dev server (ng serve) proxies /api and /ws to
 * localhost:8080 via proxy.conf.json — same-origin behavior, no CORS in dev.
 */
export const environment = {
  production: false,
  apiBaseUrl: '/api',
  wsUrl: 'ws://localhost:4200/ws',
};
