# User Service

NestJS service that owns user profile data — profile fields, privacy/notification settings, and social links. It never creates accounts itself: a profile is provisioned automatically when it consumes the `account.created` event published by `auth-service`.

> Mounted behind `api-gateway` at `/api/users/*` (prefix stripped before reaching this service). See [PROBLEMS.md](PROBLEMS.md) for known issues and the root [CLAUDE.md](../CLAUDE.md) for the cross-service contracts this service is part of.

## What this service does

- **`GET /me`** — the only HTTP endpoint currently exposed. Reads the caller's identity from the `X-User-Id` header set by the gateway (never re-verifies the JWT itself) and returns the full profile: profile fields, settings, and ordered social links.
- **Event consumption** — `ConsumerService.handleAccountCreated` listens on RabbitMQ exchange `user.events` / routing key `account.created` and creates a `User` + empty `UserProfile` + default `UserSetting` row. Idempotent by design (skips silently if the user already exists, since RabbitMQ redelivery is expected on crash/retry). Failed processing is retried up to 3 times via a dead-letter/TTL queue, then routed to a terminal `user.events.dlx` queue for manual inspection (see `PROBLEMS.md` for what happens to messages that land there today).

There are currently no endpoints to update a profile, change settings, or manage social links — see [PROBLEMS.md](PROBLEMS.md).

## Data model

PostgreSQL via Prisma (`prisma/schema.prisma`):

- `users` — the aggregate root, soft-deletable (`deletedAt`), one-to-one with `profile`/`settings`, one-to-many with `links`.
- `user_profiles` — display fields (`displayName`, `fullName`, `avatarUrl`, `bio`, `occupation`, `company`, `location`, `birthday`, `website`, `gender`, `language`, `timezone`). `email` is copied from `auth-service`'s `Account.email` at creation time — see [PROBLEMS.md](PROBLEMS.md) for the sync implications.
- `user_settings` — privacy/notification toggles (`isPrivate`, `showEmail`, `showBirthday`, `allowMessage`), locale/theme preference.
- `social_links` — ordered list of external profile links (`platform` enum, `url`, `order`).

## Requirements

- Node.js + npm.
- PostgreSQL reachable via `DATABASE_URL`.
- RabbitMQ reachable via `RABBITMQ_URL` (see [PROBLEMS.md](PROBLEMS.md) — inconsistent with `auth-service`'s variable name for the same broker, and RabbitMQ itself isn't provisioned in `infrastructure/compose.yaml` yet — see root [PROBLEMS.md](../PROBLEMS.md) item 1). Without it, this service starts but never receives `account.created` events, so newly registered accounts never get a profile.
- An OTLP gRPC trace collector if you want traces to go anywhere (defaults to `localhost:4317`).

## Configuration

Copy `.env.example` to `.env`:

```dotenv
PORT=
OTEL_EXPORTER_OTLP_ENDPOINT=
DATABASE_URL=
```

Not in `.env.example` yet, but required for the RabbitMQ consumer to actually connect — see [PROBLEMS.md](PROBLEMS.md):

```dotenv
RABBITMQ_URL=amqp://guest:guest@localhost:5672
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
src/user/           GET /me controller + service
src/consumer/        RabbitMQ topology (exchanges/queues/DLQ) + account.created handler
src/prisma/          PrismaService wrapper (uses the Postgres driver adapter, @prisma/adapter-pg)
src/common/          Global response interceptor + exception filter
```

## Related docs

- [PROBLEMS.md](PROBLEMS.md) — known issues in this service
- [Root CLAUDE.md](../CLAUDE.md) — identity headers, `account.created` event contract
- [Root PROBLEMS.md](../PROBLEMS.md) — RabbitMQ provisioning, env var naming, event schema drift
