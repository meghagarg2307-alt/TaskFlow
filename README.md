# TaskFlow

A production-grade collaborative task management platform (Trello/Jira-style) built to showcase real-world full-stack architecture.

- **Backend** Java 17 · Spring Boot · Spring Security · STOMP/WebSocket · PostgreSQL · Redis · Flyway
- **Frontend** Angular 18 (standalone, signals) · Angular CDK drag-drop · RxJS · `@stomp/stompjs`
- **Infra** Docker · docker-compose · Nginx reverse proxy
- **Auth** JWT access tokens + rotated refresh tokens (HttpOnly cookie, reuse detection)

---

## Architecture at a glance

```
                          ┌──────────────────────────┐
                          │   Browser (Angular SPA)  │
                          └─────────────┬────────────┘
                                        │ HTTP + WebSocket (STOMP)
                                        ▼
                          ┌──────────────────────────┐
                          │       Nginx (web)        │
                          │  - serves SPA bundle     │
                          │  - /api/* → backend      │
                          │  - /ws    → backend      │
                          └─────────────┬────────────┘
                                        │
                          ┌──────────────────────────┐
                          │    Spring Boot (api)     │
                          │  - REST + STOMP          │
                          │  - JWT auth + RBAC       │
                          │  - tenant scoping        │
                          └───────┬───────────┬──────┘
                                  │           │
                          ┌───────▼────┐ ┌────▼─────────┐
                          │ PostgreSQL │ │    Redis     │
                          │   (db)     │ │  (cache)     │
                          │ Flyway     │ │ pub/sub +    │
                          │ migrations │ │ membership   │
                          │            │ │ cache +      │
                          │            │ │ rate limiter │
                          └────────────┘ └──────────────┘
```

**Real-time topology**: every backend instance publishes activity events to a single Redis pub/sub channel (`taskflow:events`); every backend instance also subscribes and fans events out to its locally-connected STOMP clients. This makes horizontal scaling a flat operation — `docker compose up -d --scale api=5` just works.

---

## Quick start (local, with Docker)

```bash
# 1. Set required secrets
cp infra/.env.example infra/.env
$EDITOR infra/.env          # set DB_PASSWORD and JWT_SECRET

# 2. Bring everything up (first run takes ~3-5 minutes to build images)
docker compose -f infra/docker-compose.yml up -d --build

# 3. Tail the API until you see "Started TaskflowApplication"
docker compose -f infra/docker-compose.yml logs -f api

# 4. Open the app
open http://localhost                       # macOS
xdg-open http://localhost                   # Linux
start http://localhost                      # Windows PowerShell
```

The dev override (`infra/docker-compose.override.yml`) is applied automatically and additionally exposes:

| Port | Service | Why |
|------|---------|-----|
| 80    | nginx (`web`) | The app |
| 8080  | backend (`api`) | Direct API access for `curl` / Postman |
| 5432  | postgres (`db`) | IDE database tools |
| 6379  | redis (`cache`) | `redis-cli MONITOR` to watch pub/sub |

Stop the stack with `docker compose -f infra/docker-compose.yml down`. Add `-v` to wipe Postgres/Redis volumes.

---

## Production deployment

Run **without** the dev override and put a TLS terminator in front:

```bash
# On the host, e.g. AWS EC2:
docker compose -f infra/docker-compose.yml up -d --build
```

Recommended in front:
- **ALB / Cloudflare / Caddy** terminating TLS, forwarding to port 80 with `X-Forwarded-Proto: https`. Spring Boot's `forward-headers-strategy: framework` picks it up automatically — that's what makes `Secure` cookies work end-to-end.

Set in `infra/.env`:
```
PUBLIC_URL=https://taskflow.example.com
COOKIE_SECURE=true
COOKIE_SAMESITE=Strict
JWT_SECRET=<openssl rand -base64 48>
DB_PASSWORD=<strong random>
```

To scale the API tier horizontally:
```bash
docker compose -f infra/docker-compose.yml up -d --scale api=3
```
Redis pub/sub handles cross-instance WebSocket fan-out — no further config needed.

---

## Local development without Docker

If you'd rather run the JVM and Angular dev server on your host (faster feedback loops):

```bash
# 1. Start only the data tier in Docker
docker compose -f infra/docker-compose.yml up -d db cache

# 2. Backend (one terminal)
cd backend
./mvnw spring-boot:run

# 3. Frontend (another terminal)
cd frontend
npm install
npm start                # ng serve on :4200 with /api → :8080 proxy

# Open http://localhost:4200
```

The Angular dev server's `proxy.conf.json` forwards `/api/*` and `/ws` to `http://localhost:8080`, so the SPA code uses identical relative URLs in dev and prod.

---

## Project layout

```
backend/        Spring Boot service
  src/main/java/io/taskflow/
    common/     cross-cutting (tenant context, logging)
    config/     Spring + Redis + Web config
    controller/ REST endpoints
    domain/     JPA entities + enums
    dto/        request/response payloads
    exception/  custom exceptions + global handler
    repository/ Spring Data JPA
    security/   JWT, filters, refresh tokens
    service/    business logic (auth, project, board, task, comment, org, activity)
    websocket/  STOMP auth + broadcaster + Redis fanout subscriber
  src/main/resources/
    application.yml         base config (env-driven)
    application-prod.yml    production overlay
    db/migration/V1__*.sql  Flyway schema

frontend/       Angular SPA
  src/app/
    core/       models, auth, api clients, realtime (STOMP), interceptors
    features/   auth, projects, boards, kanban, shell

infra/
  docker-compose.yml          production-shaped stack
  docker-compose.override.yml dev conveniences
  nginx.conf                  reverse proxy + SPA fallback
  .env.example                template for required secrets
```

---

## Key design decisions

| Concern | Decision | Why |
|---|---|---|
| Multi-tenancy | Shared schema, `organization_id` discriminator | Operationally cheap; tenant scoping enforced at the repository boundary via `TenantContext`. |
| Primary keys | UUIDs | Safe to expose, distributed-friendly. |
| Drag-drop | Gap-based `BIGINT` positions (LexoRank-style) | O(1) writes per move — no neighbor renumbering. |
| Concurrent edits | `@Version` optimistic locking | Caller sends `expectedVersion`; 409 on conflict. |
| Activity feed | Append-only `activity_log` with `jsonb` payload | Immutable audit trail + efficient board/org feed queries. |
| Real-time events | DB-write-then-Redis-publish via `afterCommit` | Subscribers never see uncommitted changes. |
| Cross-instance fan-out | Redis pub/sub channel | Stateless backend → trivial horizontal scaling. |
| Auth tokens | Short JWT + rotated refresh in HttpOnly cookie | XSS-resistant; reuse detection terminates compromised sessions. |
| Refresh on the SPA | Single-flight interceptor | N parallel 401s share one refresh — avoids tripping reuse detection. |
| Optimistic UI | Trace-id round-trip | SPA tags every mutation; ignores its own WebSocket echo. |
| Rate limiting | Redis Lua fixed-window | Atomic INCR+PEXPIRE; fail-open on Redis outage. |
| Static assets | `Cache-Control: public, immutable` for hashed bundles | Cheap CDN-friendly caching with safe invalidation on each deploy. |

---

## Useful commands

```bash
# Rebuild a single service after code change
docker compose -f infra/docker-compose.yml up -d --build api

# Run backend tests
cd backend && ./mvnw test

# Run frontend lint + build
cd frontend && npm run lint && npm run build

# Inspect Postgres
docker compose -f infra/docker-compose.yml exec db psql -U taskflow -d taskflow

# Tail every service at once
docker compose -f infra/docker-compose.yml logs -f
```

---

## License

MIT.
