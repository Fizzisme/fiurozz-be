# CLAUDE.md — api-gateway

Service-specific context. Read the root [`../CLAUDE.md`](../CLAUDE.md) first for cross-service contracts (shared JWT secret, identity headers, RabbitMQ, event schema) — this file only covers what's local to the gateway.

## Purpose

Single public entry point for the Fiurozz backend. Owns: routing to internal services, JWT verification, per-route rate limiting, retry/circuit-breaking, and cross-cutting observability (structured logs, Prometheus metrics, OpenTelemetry traces). It holds no business logic and no database.

## Code map

- `cmd/server/main.go` — wires everything together and owns the process lifecycle (graceful shutdown on `SIGINT`/`SIGTERM`).
- `internal/config/` — `config.go` loads `.env` + `routes.yaml`; `routes.go` defines the `RouteConfig` shape (`Prefix`, `Upstream`, `Timeout`, `Retry`, `Breaker`, `RateLimit`, `AuthMode`).
- `internal/bootstrap/routes.go` — turns `[]RouteConfig` into registered `proxy.Route`s (builds the breaker, retry config, and `ReverseProxy` per route).
- `internal/router/` — `router.go` builds the Gin engine + global middleware chain; `builder.go` builds the **per-route** handler chain (JWT → rate limit → proxy, in that order — order matters, see `PROBLEMS.md` history).
- `internal/middleware/jwt.go` — `JWTAuth(jwtService, authMode)`. Always strips client-supplied identity headers first, then re-injects them from verified claims only when a token was actually verified.
- `internal/proxy/reverse_proxy.go` — wraps `httputil.ReverseProxy` using `Rewrite` (not the deprecated `Director`). This is where forwarded headers, prefix stripping, and trace propagation into the outbound request happen.
- `internal/resilience/` — `breaker.go` (per-route `gobreaker` instance), `breaker_transport.go` / `retry_transport.go` (composed via `resilience.NewBuilder()` in `reverse_proxy.go`).
- `internal/ratelimit/` — in-memory token-bucket per key (`"user:<id>"` or `"ip:<addr>"`, built in `internal/helper/rate_limit.go`).

## Conventions specific to this service

- **Adding a backend route:** add an entry to `configs/routes.yaml` (prefix, upstream env var reference, timeout, retry, breaker, rate limit, auth mode) and the matching upstream URL env var to `configs/.env(.example)`. Do not hard-code a new route in Go — `bootstrap.RegisterRoutes` reads config, it isn't meant to grow a new `if` branch per service.
- **Middleware order is load-bearing.** JWT authentication runs before rate limiting (`internal/router/builder.go`) specifically so the rate-limit key is derived from *verified* claims, not a client-controlled header. Don't reorder this without re-reading `internal/helper/rate_limit.go`'s `BuildRateLimitKey`.
- **Reverse proxy uses `Rewrite`, not `Director`.** If you touch `internal/proxy/reverse_proxy.go`, keep using `httputil.ProxyRequest`'s `SetURL`/`SetXForwarded` — going back to `Director` reintroduces the header-spoofing risk documented in `CODE_REVIEW.md#AGW-007`.
- **Metrics must be initialized before the breaker.** `metrics.Init()` in `main.go` must run before any `resilience.NewBreaker(...)` call, since `OnStateChange` references the global Prometheus collectors — see `CODE_REVIEW.md#AGW-005` for what happens if this order is violated (tests currently don't cover this).

## Where to look before changing things

- Auth/JWT behavior → `internal/auth/jwt.go` (verification) and `internal/middleware/jwt.go` (middleware wiring). The root CLAUDE.md's shared-secret note applies here.
- Anything about what's currently broken vs. already fixed → `PROBLEMS.md` (current) before `CODE_REVIEW.md` (dated 2026-07-23, partially stale — several P0/P1 items there are already fixed in the current code; don't rely on it without cross-checking).
- Long-term direction → `UPGRADE.md`.
