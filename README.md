# Fiurozz Backend

Fiurozz is a personal showcase backend built with a microservice architecture. It exists to demonstrate practical distributed-systems patterns — an API gateway, JWT-based auth with cross-service identity propagation, event-driven data sync via the Outbox pattern, and basic observability — rather than to run a real product in production.

> **Status:** active development, showcase/portfolio project. Not hardened for production traffic. See [PROBLEMS.md](PROBLEMS.md) for the current list of architecture-level gaps.

## Services

| Service | Language / Framework | Role | Docs |
| --- | --- | --- | --- |
| `api-gateway` | Go, Gin | Single public entry point: routing, JWT verification, rate limiting, retry/circuit breaking, metrics, tracing | [README](api-gateway/README.md) · [CLAUDE.md](api-gateway/CLAUDE.md) · [PROBLEMS.md](api-gateway/PROBLEMS.md) |
| `auth-service` | NestJS, Prisma, PostgreSQL | Registration/login (password + Google/GitHub/Facebook OAuth), token issuance, session management, publishes `account.created` events | [README](auth-service/README.md) · [CLAUDE.md](auth-service/CLAUDE.md) · [PROBLEMS.md](auth-service/PROBLEMS.md) |
| `user-service` | NestJS, Prisma, PostgreSQL | Owns user profile/settings/social links; consumes `account.created` to provision a profile after registration | [README](user-service/README.md) · [CLAUDE.md](user-service/CLAUDE.md) · [PROBLEMS.md](user-service/PROBLEMS.md) |
| `project-service` | Java, Spring Boot | Project/catalog domain. **Developed independently** — out of scope for this documentation pass; see its own [README](project-service/README.md) and `docs/architecture/`. |

## Architecture

```text
                         ┌─────────────┐
   Browser / Next.js BFT │             │
   ────────────────────► │ api-gateway │  (Go, Gin — public entry point)
                         └──────┬──────┘
                    verifies JWT, strips/re-injects
                    X-User-Id / X-User-Email / X-User-Roles
                                │
              ┌─────────────────┼─────────────────┐
              ▼                                    ▼
     ┌──────────────┐                     ┌──────────────┐
     │ auth-service │                     │ user-service │
     │  (NestJS)    │                     │  (NestJS)    │
     └──────┬───────┘                     └──────┬───────┘
            │ publishes "account.created"        │ consumes "account.created"
            │        via Outbox → RabbitMQ        │
            └─────────────────────────────────────┘

     Shared infrastructure (infrastructure/compose.yaml): Redis, Zipkin
```

Each backend service (`auth-service`, `user-service`) trusts `X-User-Id` / `X-User-Email` / `X-User-Roles` headers set by the gateway and never re-verifies the JWT itself. This keeps services simple, but it means the trust boundary is currently enforced only by convention, not by network policy — see [PROBLEMS.md](PROBLEMS.md) for the implication.

Account creation and user-profile creation are decoupled via the **Outbox pattern**: `auth-service` writes an `OutboxEvent` row in the same DB transaction as the new `Account`, a background relay publishes it to RabbitMQ, and `user-service` consumes it to create the matching `User` profile — so the two services never call each other synchronously and a crash mid-registration can't leave one side without the other.

## Getting started

There is currently no single command that brings up the full stack (see [PROBLEMS.md](PROBLEMS.md) — only `project-service` is containerized end-to-end). To run the `gateway + auth + user` slice locally:

```powershell
# 1. Shared infrastructure (Redis, Zipkin)
docker compose -f infrastructure/compose.yaml up -d

# 2. A local RabbitMQ instance is also required (not provisioned anywhere yet):
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:4-management

# 3. auth-service
cd auth-service
npm install
cp .env.example .env   # fill in DATABASE_URL, JWT_*, OAuth client credentials
npx prisma migrate deploy
npm run start:dev

# 4. user-service
cd user-service
npm install
cp .env.example .env   # fill in DATABASE_URL
npx prisma migrate deploy
npm run start:dev

# 5. api-gateway
cd api-gateway
New-Item -ItemType Directory -Force logs | Out-Null
copy configs\.env.example configs\.env   # JWT_SECRET must match auth-service's JWT_ACCESS_SECRET
go run ./cmd/server
```

Each service's own README has full configuration details and per-service known issues.

## Repository layout

```text
api-gateway/       Go/Gin reverse proxy — public entry point
auth-service/      NestJS — accounts, tokens, sessions, OAuth
user-service/      NestJS — user profiles, settings, social links
project-service/   Spring Boot — developed independently, out of scope here
infrastructure/    Shared local dev infra (Redis, Zipkin)
compose.yaml       Root compose file (includes infrastructure/ + project-service/ only)
```

## Known issues

Cross-cutting, architecture-level issues (missing RabbitMQ infra, inconsistent env var naming, shared-secret coordination, etc.) are tracked in [PROBLEMS.md](PROBLEMS.md). Issues specific to one service live in that service's own `PROBLEMS.md`.
