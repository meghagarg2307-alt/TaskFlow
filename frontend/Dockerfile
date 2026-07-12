# syntax=docker/dockerfile:1.6
# ---------------------------------------------------------------------------
# TaskFlow web tier (Angular build + Nginx reverse proxy).
#
# Single container does double duty: it serves the SPA bundle AND fronts the
# backend API + WebSocket. Build context: repository root.
#   build: { context: .., dockerfile: frontend/Dockerfile }
#
# Upstream is NOT baked into the image. At start, the official nginx entrypoint
# runs envsubst on /etc/nginx/templates/*.template → /etc/nginx/conf.d/, using:
#   API_UPSTREAM, API_HOST, NGINX_RESOLVER
# ---------------------------------------------------------------------------

# ============ Stage 1: build the SPA ============
FROM node:20-alpine AS build
WORKDIR /workspace

# Dependencies layer — re-runs only when lockfile changes.
COPY frontend/package.json frontend/package-lock.json* ./
RUN npm ci --no-audit --no-fund --silent

# Sources — invalidates only this layer on code changes.
COPY frontend/ ./
RUN npm run build

# ============ Stage 2: nginx runtime ============
FROM nginx:1.27-alpine AS runtime

# Drop the stock catch-all; our template becomes conf.d/default.conf at start.
RUN rm -f /etc/nginx/conf.d/default.conf

# Template → envsubst at container start (official nginx docker entrypoint).
COPY infra/nginx.conf.template /etc/nginx/templates/default.conf.template

# Fail fast if required upstream env vars are missing.
COPY frontend/docker-entrypoint.d/15-check-api-upstream.sh \
     /docker-entrypoint.d/15-check-api-upstream.sh
RUN chmod +x /docker-entrypoint.d/15-check-api-upstream.sh

# Angular 17+ application builder emits to dist/<project>/browser/.
COPY --from=build /workspace/dist/taskflow/browser /usr/share/nginx/html

# Defaults match Docker Compose (override on Render via service env).
# NGINX_ENVSUBST_FILTER limits envsubst to these names so nginx $vars stay intact.
ENV API_UPSTREAM=http://api:8080 \
    API_HOST=api \
    NGINX_RESOLVER=127.0.0.11 \
    NGINX_ENVSUBST_FILTER=^(API_|NGINX_RESOLVER$)

EXPOSE 80

# Light healthcheck — index.html being served means nginx is alive AND the SPA
# bundle made it into the image (a misconfigured COPY would 404).
HEALTHCHECK --interval=15s --timeout=3s --retries=3 \
  CMD wget -qO- http://localhost/ > /dev/null || exit 1
