/**
 * Production environment. Compiled into the bundle for prod builds.
 * Dev builds get `environment.development.ts` substituted by angular.json.
 *
 * All URLs are relative — production traffic goes through Nginx which routes
 * /api/* → backend and /ws → backend on the same origin. No CORS in prod.
 */
export const environment = {
  production: true,
  apiBaseUrl: '/api',
  wsUrl: (typeof window !== 'undefined' && window.location.protocol === 'https:' ? 'wss' : 'ws')
    + '://'
    + (typeof window !== 'undefined' ? window.location.host : '')
    + '/ws',
};
