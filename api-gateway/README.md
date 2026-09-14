# API Gateway

Go/Gin reverse proxy that sits in front of Fiurozz's internal services. It terminates client traffic, verifies JWTs, applies per-route rate limiting/retry/circuit-breaking, and forwards requests to the appropriate backend, while exporting Prometheus metrics and OpenTelemetry traces.

> **Status:** actively developed, not production-hardened. Most of the P0 issues from the 2026-07-23 audit ([CODE_REVIEW.md](CODE_REVIEW.md)) have since been fixed — see [PROBLEMS.md](PROBLEMS.md) for what's still open today. `CODE_REVIEW.md` and `UPGRADE.md` are kept as historical audit/roadmap documents; don't treat them as the current state without checking `PROBLEMS.md` first.

## Request flow

```text
Client
  → Recovery → RequestID → ClientInfo → OpenTelemetry → Logger → Metrics → CORS
  → [per route] JWTAuth(authMode) → RateLimit → ReverseProxy (retry + circuit breaker)
  → backend service
```

`authMode`, timeout, retry policy, circuit breaker, and rate limit are all declared per route in `configs/routes.yaml` — nothing is hard-coded per backend in Go.

## Routes

Currently registered in [`configs/routes.yaml`](configs/routes.yaml):

| Prefix | Upstream | Auth mode | Notes |
| --- | --- | --- | --- |
| `/api/auth/*path` | `AUTH_SERVICE` | `optional` | Prefix stripped before forwarding (`/api/auth/login` → `/login` at auth-service) |
| `/api/users/*path` | `USER_SERVICE` | `optional` | Same prefix-stripping behavior |

Plus built-in endpoints:

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/health` | Liveness check — always `{"status":"ok"}`, does not check dependencies |
| `GET` | `/metrics` | Prometheus metrics — currently public, no auth/network restriction |

`authMode: optional` lets a request through with or without a token; the identity headers below are only set when a valid token is present. Use `required` for routes that must reject unauthenticated requests, and `none` for fully public routes — see `internal/config/routes.go`.

## Identity propagation

On a request with a valid `Authorization: Bearer <token>`, the gateway:

1. Verifies the JWT (HMAC, `JWT_SECRET` + `JWT_ISSUER`).
2. Strips any client-supplied `X-User-Id` / `X-User-Email` / `X-User-Roles` headers (so a caller can't spoof them).
3. Re-injects those headers from the verified claims before proxying.
4. Uses the verified user ID (not a header) as the rate-limit key, falling back to client IP only when there's no authenticated identity.

Downstream services trust these headers completely and never re-verify the JWT themselves — see the root [CLAUDE.md](../CLAUDE.md) for why this is a convention, not an enforced boundary, and the root [PROBLEMS.md](../PROBLEMS.md) for the implication.

## Requirements

- Go `1.26.4` (per `go.mod`).
- `auth-service` and `user-service` running and reachable at the URLs configured below.
- An OTLP gRPC trace receiver if you want traces to go anywhere — `docker compose up -d` from the repo root now provisions Jaeger for this (see [PROBLEMS.md](PROBLEMS.md), AGW-014).
- Running natively (`go run`): a `logs/` directory must exist first, since the logger writes to `logs/gateway.log` (see [PROBLEMS.md](PROBLEMS.md), AGW-010). The Docker image creates this itself.

## Configuration

For local/native runs, `config.Load()` reads `configs/.env` (copy `configs/.env.example` and fill in real values — never commit the result). In a container, a missing `configs/.env` is no longer fatal — real values come from Compose's `env_file`/`environment` instead (see [PROBLEMS.md](PROBLEMS.md), AGW-009).

```dotenv
APP_NAME=api-gateway
PORT=8080
OTEL_ENDPOINT=localhost:4317
JWT_SECRET=<must match auth-service's JWT_ACCESS_SECRET — see root CLAUDE.md>
JWT_ISSUER=<must match auth-service's JWT_ISSUER>
ACCESS_TOKEN_EXPIRE=<currently loaded but unused>
AUTH_SERVICE=http://localhost:<auth-service-port>
USER_SERVICE=http://localhost:<user-service-port>
PROJECT_SERVICE=
MEMBER_SERVICE=
CHAT_SERVICE=
NOTIFICATION_SERVICE=
```

| Variable | Required | Status |
| --- | --- | --- |
| `APP_NAME` | Yes | Used in logs/traces |
| `PORT` | Yes | HTTP listen port |
| `OTEL_ENDPOINT` | Yes, if tracing matters | OTLP gRPC endpoint |
| `JWT_SECRET` | Yes | Must match `auth-service`'s `JWT_ACCESS_SECRET` byte-for-byte |
| `JWT_ISSUER` | Yes | Must match `auth-service`'s `JWT_ISSUER` |
| `ACCESS_TOKEN_EXPIRE` | No | Loaded but never read |
| `AUTH_SERVICE` | Yes | Registered route upstream |
| `USER_SERVICE` | Yes | Registered route upstream |
| `PROJECT_SERVICE`, `MEMBER_SERVICE`, `CHAT_SERVICE`, `NOTIFICATION_SERVICE` | No | Loaded but no route registered for any of them yet |

## Run locally

From the `api-gateway` directory:

```powershell
New-Item -ItemType Directory -Force logs | Out-Null
go mod download
go run ./cmd/server
```

Smoke test:

```powershell
Invoke-RestMethod http://localhost:8080/health
Invoke-WebRequest http://localhost:8080/metrics
```

## Observability stack

Prometheus, Grafana, Jaeger, Loki and Promtail are provisioned in the shared `infrastructure/compose.yaml` at the repo root (on the same Docker network as every service, so this only works when the gateway itself is also running via Compose — see [PROBLEMS.md](PROBLEMS.md), AGW-014):

```powershell
docker compose up -d
```

- Prometheus: `http://localhost:9090` (scrapes `api-gateway:8080/metrics`)
- Grafana: `http://localhost:3001` (mapped off the default 3000 to avoid clashing with a locally-running Next.js app; add Prometheus at `http://prometheus:9090` and Loki at `http://loki:3100` as data sources — not pre-provisioned)
- Jaeger UI: `http://localhost:16686` (receives OTLP traces from `api-gateway`, `auth-service`, and `user-service`)

Still uses unpinned/EOL images (Promtail, Jaeger v1) — see [CODE_REVIEW.md](CODE_REVIEW.md#agw-014) for the historical detail and [UPGRADE.md](UPGRADE.md) for the migration plan.

## Quality checks

```powershell
gofmt -w .
go mod tidy
go test ./...
go vet ./...
```

See [PROBLEMS.md](PROBLEMS.md) for the current pass/fail status of each of these.

## Structure

```text
cmd/server/          Dependency wiring + HTTP server entry point
configs/              Local config (routes.yaml, .env — never commit real secrets)
deploy/               Local Prometheus/Grafana/Jaeger/Loki/Promtail stack
internal/auth/        JWT claims + verification
internal/bootstrap/   Route registration from config
internal/config/      Env var + routes.yaml loading
internal/logger/      Zap logger
internal/metrics/     Prometheus collectors
internal/middleware/  Gin middleware (JWT, rate limit, request ID, logging, metrics, client info)
internal/proxy/       Route registry + reverse proxy (Rewrite-based)
internal/ratelimit/   Per-key token-bucket limiter (in-memory)
internal/resilience/  Retry + circuit breaker transport
internal/router/      Route/middleware chain assembly
internal/tracing/     OpenTelemetry tracer provider
```

## Related docs

- [PROBLEMS.md](PROBLEMS.md) — current known issues in this service
- [CODE_REVIEW.md](CODE_REVIEW.md) — full audit from 2026-07-23 (historical; cross-check against `PROBLEMS.md`)
- [UPGRADE.md](UPGRADE.md) — staged roadmap toward a production-ready gateway
- [Root CLAUDE.md](../CLAUDE.md) — cross-service contracts (shared JWT secret, identity headers, routing prefixes)
