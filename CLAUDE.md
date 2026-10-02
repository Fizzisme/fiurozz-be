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
| `user-service` | NestJS 11, Prisma 7 | `.env` → `PORT` | PostgreSQL (`users`, `user_profiles`, `user_settings`, `social_links`, `outbox_events`) | `src/main.ts` |
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
3. **Events on exchange `user.events`.** Every event is written to the publisher's `outbox_events` table in the same transaction as the business change, then published by that service's `OutboxRelayService` with the event type as the routing key.

   | Event | Published by | Payload | Consumed by |
   | --- | --- | --- | --- |
   | `account.created` | `auth-service` (password register and OAuth signup) | `id`, `email`, `fullName`, `displayName`; plus `avatarUrl` (OAuth only) or `country`, `birthday`, `gender` (password only) | `user-service` (`ConsumerService.handleAccountCreated`); `project-service` |
   | `user.profile.updated` | `user-service` (`UserService.updateProfile`, only when `displayName` actually changes) | `userId`, `displayName` | `auth-service` (`ConsumerService.handleUserProfileUpdated`, updates `Account.displayName`); `project-service` |
   | `user.avatar.updated` | `user-service` (avatar upload, replace and delete; not cover) | `userId`, `avatarUrl` (`null` when removed) | `project-service` |

   The payload shapes are **not shared**: each publisher and each consumer declares its own type inline, and the two `account.created` producer call sites (password register vs. OAuth signup) don't send the same fields — see each service's `PROBLEMS.md` and root `PROBLEMS.md` item 5. Before changing an event's shape, read the publisher (`auth-service/src/outboxEvent/` and its call sites, or `user-service/src/user/user.service.ts`) and every consumer in the table. `displayName` is unique in both `auth-service` (`Account`) and `user-service` (`UserProfile`), so a rename can be rejected by either side; `auth-service` logs and drops a rename that collides rather than retrying it. All consumers must stay idempotent, since the broker redelivers. `project-service` is developed on its own track (see above); it is listed here only because it consumes these events, and its handling lives in its own docs.
4. **RabbitMQ topology.** `auth-service` and `user-service` each declare exchanges `user.events`, `user.events.retry` and `user.events.dlx` (all topic) in their own `src/app-rabbitmq/app-rabbitmq.module.ts` — `RabbitMQModule.forRoot` is called only there, and the outbox relay and the consumer module both import that module instead of opening a second connection. Each consumer owns its queues and its retry/dead-letter wiring (for example `user-service.account-created` and `auth-service.user-profile-updated`, each with a `.retry` queue and a `.failed` queue). The broker itself is defined in `infrastructure/compose.yaml`; note the connection variable is named `RABBIT_MQ_URI` in `auth-service` but `RABBITMQ_URL` in `user-service` and `project-service`.
5. **Gateway routing prefixes.** `/api/auth/*` and `/api/users/*` are stripped to `/*` by the gateway before reaching each service (e.g. `/api/auth/login` → `auth-service` sees `POST /login`). Service controllers are written with no path prefix of their own, on the assumption the gateway always does this stripping.

## Conventions observed in this repo

- **Commits:** Conventional Commits, scoped to the service: `feat(auth): ...`, `fix(gateway): ...`, `refactor(user): ...`. Follow this in any new commit.
- **Branches / PRs:** `feature/<service>/<slug>` branches, merged via PR (see `git log` — nearly every commit on `main` is a merge commit). Don't push directly to `main`.
- **NestJS services:** ESM (`"type": "module"` in `package.json`), so relative imports use explicit `.js` extensions even in `.ts` source (e.g. `import { AuthService } from './auth.service.js'`). This is required by the TS/ESM setup, not a typo — don't "fix" it.
- **Prisma:** generated client lives in `src/generated/prisma/` (not `node_modules`) per each service's `prisma/schema.prisma` `generator client { output = ... }`. Regenerate with `npx prisma generate` after schema changes; never hand-edit files under `src/generated/`.
- **DTO validation:** both NestJS services enable a global `ValidationPipe` with `whitelist: true, forbidNonWhitelisted: true, transform: true`. New DTO fields need explicit `class-validator` decorators or requests will be rejected.

## Exploring the codebase

A knowledge graph of this repo lives in `graphify-out/`. Before exploring for a new feature or a cross-file question, read `graphify-out/GRAPH_REPORT.md` and try `/graphify query "<question>"` before broad grepping. Treat it as a map, not the truth: it reflects the code at the last scan, so confirm in the real files before editing, and re-run `/graphify` (update) after large changes.

## Working agreements for AI sessions

- Each service's `PROBLEMS.md` is the current issue tracker for that service; the root `PROBLEMS.md` covers cross-cutting/architecture issues. Check the relevant one before assuming something is a fresh discovery — and update it if you fix or discover something.
- `api-gateway/CODE_REVIEW.md` and `api-gateway/UPGRADE.md` are a dated (2026-07-23) audit and upgrade roadmap for the gateway. Several items in `CODE_REVIEW.md` have since been fixed (check `api-gateway/PROBLEMS.md` for current status) — don't treat that file as up to date without cross-checking the code.
- Never commit `.env` files (already correctly gitignored in `auth-service` and `user-service`; `api-gateway`'s `configs/.env` was tracked once historically — verify it's still untracked before touching gateway config).
- Don't add new cross-service infrastructure (message brokers, caches, service discovery) to `infrastructure/compose.yaml` without checking whether it's already assumed to exist by a service's code — several such gaps already exist (see root `PROBLEMS.md`).
- When changing anything under `project-service/`, stop and confirm with the user first — it's explicitly out of scope for this documentation effort and is being developed on its own track.
