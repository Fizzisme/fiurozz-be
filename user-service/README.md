# User Service

NestJS service that owns user profile data — profile fields, privacy/notification settings, and social links. It never creates accounts itself: a profile is provisioned automatically when it consumes the `account.created` event published by `auth-service`.

> Mounted behind `api-gateway` at `/api/users/*` (prefix stripped before reaching this service). See [PROBLEMS.md](PROBLEMS.md) for known issues and the root [CLAUDE.md](../CLAUDE.md) for the cross-service contracts this service is part of.

## What this service does

- **`GET /me`** — returns the authenticated user's full profile.
- **`PATCH /me`** — updates textual profile fields and skills. Avatar and cover URLs are server-owned and cannot be supplied here.
- **`POST /me/avatar`** — backward-compatible upload endpoint using multipart field `file` (JPEG, PNG, or WebP; maximum 5 MB).
- **`PUT /me/avatar`** — canonical create-or-replace endpoint using the same multipart contract, so clients do not need to know whether an avatar already exists.
- **`DELETE /me/avatar`** — clears the profile's avatar pointer. Content-addressed S3 objects are retained for safe grace-period garbage collection.
- **`POST /me/cover`** — backward-compatible cover upload endpoint using multipart field `file` (JPEG, PNG, or WebP; maximum 10 MB).
- **`PUT /me/cover`** — canonical create-or-replace endpoint using the same multipart contract.
- **`DELETE /me/cover`** — clears the cover pointer while retaining the immutable object for safe grace-period garbage collection.
- **Image processing** — decodes trusted image formats with Sharp, strips EXIF/GPS metadata, and stores WebP variants (`64x64`/`256x256` for avatars and `640x240`/`1280x480` for covers).
- **Storage cleanup** — runs daily at 03:00 UTC and deletes unreferenced profile-image groups only after the configured 24-hour grace period.
- **Upload quota** — limits profile-image uploads per user (default 5 uploads per 60 seconds) before multipart parsing begins.
- **`GET /` and `GET /:identifier`** — public member listing and profile detail endpoints.
- **Event consumption** — `ConsumerService.handleAccountCreated` listens on RabbitMQ exchange `user.events` / routing key `account.created` and creates a `User` + empty `UserProfile` + default `UserSetting` row. Idempotent by design (skips silently if the user already exists, since RabbitMQ redelivery is expected on crash/retry). Failed processing is retried up to 3 times via a dead-letter/TTL queue, then routed to a terminal `user.events.dlx` queue for manual inspection (see `PROBLEMS.md` for what happens to messages that land there today).

## Data model

PostgreSQL via Prisma (`prisma/schema.prisma`):

- `users` — the aggregate root, soft-deletable (`deletedAt`), one-to-one with `profile`/`settings`, one-to-many with `links`.
- `user_profiles` — display fields plus the current public avatar/cover URLs, private S3 object keys, and JSON metadata for each generated image variant. Image bytes live in S3/MinIO, not PostgreSQL. `email` is copied from `auth-service`'s `Account.email` at creation time — see [PROBLEMS.md](PROBLEMS.md) for the sync implications.
- `user_settings` — privacy/notification toggles (`isPrivate`, `showEmail`, `showBirthday`, `allowMessage`), locale/theme preference.
- `social_links` — ordered list of external profile links (`platform` enum, `url`, `order`).

## Requirements

- Node.js + npm.
- PostgreSQL reachable via `DATABASE_URL`.
- RabbitMQ reachable via `RABBITMQ_URL`. Root Compose provisions it for local development. Without it, this service starts but never receives `account.created` events, so newly registered accounts never get a profile.
- An S3-compatible object store. Root Compose provisions MinIO for local development.
- An OTLP gRPC trace collector if you want traces to go anywhere (defaults to `localhost:4317`).

## Configuration

Copy `.env.example` to `.env`:

```dotenv
PORT=8082
OTEL_EXPORTER_OTLP_ENDPOINT=
DATABASE_URL=
S3_ENDPOINT=http://localhost:9000
S3_REGION=us-east-1
S3_ACCESS_KEY=minioadmin
S3_SECRET_KEY=minioadmin
S3_BUCKET=user-media
S3_PUBLIC_BASE_URL=http://localhost:9000
PROFILE_IMAGE_GC_ENABLED=true
PROFILE_IMAGE_GC_GRACE_HOURS=24
PROFILE_IMAGE_UPLOAD_LIMIT=5
PROFILE_IMAGE_UPLOAD_WINDOW_SECONDS=60
```

The upstream MinIO Community project is source-only, so Compose builds the local-development image from the pinned official commit declared in `infrastructure/minio/Dockerfile`; it does not depend on an unofficial registry mirror. For production, point the same S3-compatible adapter at maintained AWS S3 or a currently supported MinIO AIStor deployment instead of using this archived Community build.

Example through the gateway:

```bash
curl -X POST http://localhost:8080/api/users/me/avatar \
  -H "Authorization: Bearer <access-token>" \
  -F "file=@avatar.png"
```

The RabbitMQ consumer also requires:

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
npm run test       # unit and controller tests
npm run test:e2e   # e2e tests — see PROBLEMS.md, the test/ directory doesn't exist yet
```

## Structure

```text
src/user/           Profile read/update and avatar/cover upload endpoints
src/storage/        Sharp processing, S3-compatible storage, and scheduled garbage collection
src/consumer/        RabbitMQ topology (exchanges/queues/DLQ) + account.created handler
src/prisma/          PrismaService wrapper (uses the Postgres driver adapter, @prisma/adapter-pg)
src/common/          Global response interceptor + exception filter
```

## Related docs

- [PROBLEMS.md](PROBLEMS.md) — known issues in this service
- [Root CLAUDE.md](../CLAUDE.md) — identity headers, `account.created` event contract
- [Root PROBLEMS.md](../PROBLEMS.md) — RabbitMQ provisioning, env var naming, event schema drift
