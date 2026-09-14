# CLAUDE.md — Fiurozz Backend

Context for any AI session (Claude Code or otherwise) working in this repository. Read this before touching more than one service — it captures the cross-service contracts that aren't visible from inside any single service's codebase.

## What this project is

Fiurozz is a **personal showcase/portfolio backend**, built to demonstrate microservice patterns (gateway, JWT propagation, event-driven sync, observability). It is not a production system serving real users — treat findings and priorities accordingly: correctness and clarity matter more than production-grade hardening, but the security-relevant issues tracked in `PROBLEMS.md` files are still worth fixing since they reflect real gaps, not just cosmetics.

`project-service` (Spring Boot) is **developed independently** by the same author on a separate track with its own docs (`project-service/docs/architecture/`). Do not modify it, review it, or fold it into cross-service documentation unless explicitly asked — this file and its siblings deliberately exclude it.

## Services at a glance

| Service | Stack | Default port source | Datastore | Entry point |
| --- | --- | --- | --- | --- |
| `api-gateway` | Go 1.26, Gin | `configs/.env` → `PORT` | none (stateless) | `cmd/server/main.go` |
| `auth-service` | NestJS 11, Prisma 7 | `.env` → `PORT` | PostgreSQL (`accounts`, `oauth_accounts`, `refresh_tokens`, `outbox_events`) | `src/main.ts` |
| `user-service` | NestJS 11, Prisma 7 | `.env` → `PORT` | PostgreSQL (`users`, `user_profiles`, `user_settings`, `social_links`) | `src/main.ts` |
| `project-service` | Spring Boot | — | PostgreSQL (`product_service`) | out of scope |

Each NestJS service has its own Prisma schema and its own Postgres database — there is no shared database. All cross-service data flow goes through the gateway (synchronous, request/response) or RabbitMQ (asynchronous, events).

## Request flow

```text
Client → api-gateway → [Recovery, RequestID, ClientInfo, otelgin, Logger, Metrics, CORS]
       → per-route: JWTAuth(authMode) → RateLimit → ReverseProxy (retry + circuit breaker)
       → auth-service (/api/auth/*) or user-service (/api/users/*)
```

Route-level policy (auth mode, timeout, retry, breaker, rate limit) is declared per-route in `api-gateway/configs/routes.yaml`, not hard-coded in Go. When adding a new backend service, add a route entry there and an upstream URL env var in `configs/.env` — see `api-gateway/CLAUDE.md`.

## Cross-service contracts (the part that isn't visible from inside one service)

These are the pieces that must stay in sync **by hand** across repos/services. There is currently no shared package, schema registry, or startup check enforcing any of them — that absence is itself tracked in the root `PROBLEMS.md`.

1. **JWT shared secret.** `api-gateway`'s `JWT_SECRET` + `JWT_ISSUER` (HMAC verification) must exactly match `auth-service`'s `JWT_ACCESS_SECRET` + `JWT_ISSUER` (HMAC signing). The refresh token uses a *separate* secret (`JWT_REFRESH_SECRET`) that only `auth-service` ever sees — the gateway never verifies refresh tokens.
2. **Identity headers.** The gateway verifies the JWT and sets `X-User-Id`, `X-User-Email`, `X-User-Roles` on the outbound request, after stripping any client-supplied values with the same names. `auth-service` and `user-service` trust these headers completely and never re-verify the JWT. This is a convention, not a network-enforced boundary — see `PROBLEMS.md`.
3. **`account.created` event.** Published by `auth-service` (via the Outbox pattern → RabbitMQ exchange `user.events`, routing key `account.created`) and consumed by `user-service` (`ConsumerService.handleAccountCreated`). The payload shape is **not shared** between the two services — each defines its own TypeScript type inline, and the two producer call sites (password register vs. OAuth signup) don't send the same fields. Before changing this event's shape in either service, read both `auth-service/src/outboxEvent/` and `user-service/src/consumer/consumer.service.ts` — see each service's `PROBLEMS.md` for the specific mismatch.
4. **RabbitMQ topology.** Exchange `user.events` (topic) is declared independently by both `auth-service/src/outboxEvent/outbox-event.module.ts` (publisher) and `user-service/src/consumer/consumer.module.ts` (consumer, plus `user.events.retry` / `user.events.dlx` for its retry/DLQ setup). RabbitMQ itself is **not provisioned anywhere** in this repo's compose files — see root `PROBLEMS.md` item 1 before assuming `docker compose up` gives you a working broker.
5. **Gateway routing prefixes.** `/api/auth/*` and `/api/users/*` are stripped to `/*` by the gateway before reaching each service (e.g. `/api/auth/login` → `auth-service` sees `POST /login`). Service controllers are written with no path prefix of their own, on the assumption the gateway always does this stripping.

## Conventions observed in this repo

- **Commits:** Conventional Commits, scoped to the service: `feat(auth): ...`, `fix(gateway): ...`, `refactor(user): ...`. Follow this in any new commit.
- **Branches / PRs:** `feature/<service>/<slug>` branches, merged via PR (see `git log` — nearly every commit on `main` is a merge commit). Don't push directly to `main`.
- **NestJS services:** ESM (`"type": "module"` in `package.json`), so relative imports use explicit `.js` extensions even in `.ts` source (e.g. `import { AuthService } from './auth.service.js'`). This is required by the TS/ESM setup, not a typo — don't "fix" it.
- **Prisma:** generated client lives in `src/generated/prisma/` (not `node_modules`) per each service's `prisma/schema.prisma` `generator client { output = ... }`. Regenerate with `npx prisma generate` after schema changes; never hand-edit files under `src/generated/`.
- **DTO validation:** both NestJS services enable a global `ValidationPipe` with `whitelist: true, forbidNonWhitelisted: true, transform: true`. New DTO fields need explicit `class-validator` decorators or requests will be rejected.

## Working agreements for AI sessions

- Each service's `PROBLEMS.md` is the current issue tracker for that service; the root `PROBLEMS.md` covers cross-cutting/architecture issues. Check the relevant one before assuming something is a fresh discovery — and update it if you fix or discover something.
- `api-gateway/CODE_REVIEW.md` and `api-gateway/UPGRADE.md` are a dated (2026-07-23) audit and upgrade roadmap for the gateway. Several items in `CODE_REVIEW.md` have since been fixed (check `api-gateway/PROBLEMS.md` for current status) — don't treat that file as up to date without cross-checking the code.
- Never commit `.env` files (already correctly gitignored in `auth-service` and `user-service`; `api-gateway`'s `configs/.env` was tracked once historically — verify it's still untracked before touching gateway config).
- Don't add new cross-service infrastructure (message brokers, caches, service discovery) to `infrastructure/compose.yaml` without checking whether it's already assumed to exist by a service's code — several such gaps already exist (see root `PROBLEMS.md`).
- When changing anything under `project-service/`, stop and confirm with the user first — it's explicitly out of scope for this documentation effort and is being developed on its own track.
