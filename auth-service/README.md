# Auth Service

NestJS service that owns account identity: registration, password login, OAuth (Google/GitHub/Facebook), access/refresh token issuance, and session management. Publishes an `account.created` event (via the Outbox pattern) so `user-service` can provision a matching profile.

> Mounted behind `api-gateway` at `/api/auth/*` (prefix stripped before reaching this service — e.g. `/api/auth/login` arrives here as `POST /login`). See [PROBLEMS.md](PROBLEMS.md) for known issues and the root [CLAUDE.md](../CLAUDE.md) for the cross-service contracts this service is part of.

## What this service does

- **Password auth:** `POST /register`, `POST /login` — bcrypt-hashed passwords, one account per email.
- **OAuth:** `GET /oauth/{google,github,facebook}` (redirect to provider) and `.../callback` (Passport strategy verifies the profile, then issues a short-lived handoff code redirecting back to the frontend). `POST /oauth/exchange` trades that handoff code for real tokens — this indirection exists so a popup closed mid-flow never leaves an orphaned session (see comments in `auth.controller.ts`).
- **Tokens:** `POST /refresh` rotates the access/refresh pair. Access and refresh tokens are signed with **separate** secrets (`JWT_ACCESS_SECRET`, `JWT_REFRESH_SECRET`); only the access-token secret needs to match `api-gateway`'s `JWT_SECRET` (see root [CLAUDE.md](../CLAUDE.md)).
- **Sessions:** `GET /sessions`, `DELETE /sessions/:id`, `POST /logout`, `POST /logout-all` — one row per refresh token in `refresh_tokens`, keyed by device/IP/user-agent, so a user can see and revoke individual "logged in devices."
- **Event publishing:** every account creation (password or OAuth) writes an `OutboxEvent` row in the same DB transaction as the `Account` row. `OutboxRelayService` polls that table every 2s and publishes to RabbitMQ (exchange `user.events`, routing key `account.created`), retrying up to 3 times before marking an event `failed`.

`sessions`/`logout*` endpoints read the caller's identity from the `X-User-Id` header set by the gateway — this service never re-verifies the JWT itself.

## Data model

PostgreSQL via Prisma (`prisma/schema.prisma`):

- `accounts` — identity + credentials (`passwordHash` nullable for OAuth-only accounts), `roles: String[]` (default `["USER"]`).
- `oauth_accounts` — one row per linked provider identity, cascade-deleted with the account.
- `refresh_tokens` — one row per active session; token stored as a bcrypt hash, never in plaintext.
- `outbox_events` — the Outbox pattern's event log (`pending` → `success`/`failed`, with `attempts` tracked).

## Requirements

- Node.js + npm.
- PostgreSQL reachable via `DATABASE_URL`.
- RabbitMQ reachable via the connection string this service reads (see [PROBLEMS.md](PROBLEMS.md) — the variable name is inconsistent with `user-service`, and RabbitMQ itself isn't provisioned in `infrastructure/compose.yaml` yet — see root [PROBLEMS.md](../PROBLEMS.md) item 1).
- OAuth app credentials (Google/GitHub/Facebook) if exercising those flows locally.
- An OTLP gRPC trace collector if you want traces to go anywhere (defaults to `localhost:4317`).

## Configuration

Copy `.env.example` to `.env` and fill in real values:

```dotenv
PORT=
JWT_ACCESS_SECRET=      # must match api-gateway's JWT_SECRET
JWT_REFRESH_SECRET=     # separate secret, only used here
JWT_ACCESS_EXPIRES=     # e.g. 15m
JWT_REFRESH_EXPIRES=    # e.g. 7d
JWT_ISSUER=             # must match api-gateway's JWT_ISSUER
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
GOOGLE_CALLBACK_URL=
GITHUB_CLIENT_ID=
GITHUB_CLIENT_SECRET=
GITHUB_CALLBACK_URL=
FACEBOOK_CLIENT_ID=
FACEBOOK_CLIENT_SECRET=
FACEBOOK_CALLBACK_URL=
OTEL_EXPORTER_OTLP_ENDPOINT=
OAUTH_HANDOFF_SECRET=   # see PROBLEMS.md — do not leave unset
FE_URL=                 # frontend origin OAuth callbacks redirect to
DATABASE_URL=
```

Not in `.env.example` yet, but required for the outbox relay to actually publish anything — see [PROBLEMS.md](PROBLEMS.md):

```dotenv
RABBIT_MQ_URI=amqp://guest:guest@localhost:5672
```

## Run locally

```powershell
npm install
npx prisma generate
npx prisma migrate deploy
npm run start:dev
```

## Tests

```powershell
npm run test       # unit tests — see PROBLEMS.md, there are currently none written
npm run test:e2e   # e2e tests — see PROBLEMS.md, the test/ directory doesn't exist yet
```

## Structure

```text
src/auth/            Controller/service for register, login, refresh, logout, sessions
src/auth/jwt/         Token generation/verification (JwtTokenService)
src/auth/token/       Session persistence (RefreshTokenService) + shared issuance logic (TokenService)
src/auth/password/    bcrypt hashing/comparison
src/account/          Account CRUD (find/create), independent of auth flow specifics
src/oauth-account/    Passport strategies + guards (Google/GitHub/Facebook) and account linking
src/outboxEvent/       Outbox write side (OutboxEventService) + relay/publisher (OutboxRelayService)
src/prisma/            PrismaService wrapper
src/common/            Global response interceptor + exception filter
```

## Related docs

- [PROBLEMS.md](PROBLEMS.md) — known issues in this service
- [Root CLAUDE.md](../CLAUDE.md) — shared JWT secret, identity headers, `account.created` event contract
- [Root PROBLEMS.md](../PROBLEMS.md) — RabbitMQ provisioning, env var naming, event schema drift
