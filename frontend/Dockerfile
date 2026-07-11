# syntax=docker/dockerfile:1.6
# ---------------------------------------------------------------------------
# TaskFlow web tier (Angular build + Nginx reverse proxy).
#
# Single container does double duty: it serves the SPA bundle AND fronts the
# backend API + WebSocket. Build context: repository root.
#   build: { context: .., dockerfile: frontend/Dockerfile }
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

# Drop the stock catch-all server block; ours owns port 80.
RUN rm /etc/nginx/conf.d/default.conf

# Reverse-proxy + SPA-fallback config lives in infra/, shared with ops.
COPY infra/nginx.conf /etc/nginx/conf.d/taskflow.conf

# Angular 17+ application builder emits to dist/<project>/browser/.
COPY --from=build /workspace/dist/taskflow/browser /usr/share/nginx/html

EXPOSE 80

# Light healthcheck — index.html being served means nginx is alive AND the SPA
# bundle made it into the image (a misconfigured COPY would 404).
HEALTHCHECK --interval=15s --timeout=3s --retries=3 \
  CMD wget -qO- http://localhost/ > /dev/null || exit 1
