# CLAUDE.md — user-service

Service-specific context. Read the root [`../CLAUDE.md`](../CLAUDE.md) first — it covers the identity-header contract and the events on `user.events` (this service consumes `account.created` and publishes `user.profile.updated` and `user.avatar.updated`).

## Purpose

Owns user-facing profile data (profile, settings, social links). Deliberately doesn't own account/credential data — that's `auth-service`. The two are kept eventually-consistent via events, not a synchronous call, so `user-service` can be down without blocking registration, and vice versa: `account.created` creates the profile here, and a display name change here is sent back to `auth-service` as `user.profile.updated`. `user.avatar.updated` is for other services (`project-service`) that keep a copy of the owner's name and avatar.

## Code map

- `src/user/user.controller.ts` / `user.service.ts` — the HTTP surface: `GET` and `PATCH /me`, `POST`/`PUT`/`DELETE /me/avatar` and `/me/cover`, `GET /` (list) and `GET /:identifier`. Trusts `X-User-Id` completely (see root `CLAUDE.md` contract #2) — the guard in `user.service.ts#getMe` that throws if the header is missing is defense-in-depth, not the actual auth check (that already happened at the gateway, when it runs — see this service's `PROBLEMS.md` for a config gap here). Follow endpoints live in `src/follow/`; profile image storage in `src/storage/`.
- `src/app-rabbitmq/app-rabbitmq.module.ts` — the one place `RabbitMQModule.forRoot` is called, and the full RabbitMQ topology this service depends on: main exchange `user.events`, a retry exchange (`user.events.retry`, TTL-based backoff via `x-message-ttl`), and a dead-letter exchange (`user.events.dlx`) for messages that exhaust retries. The consumer and the outbox relay both import it; never call `forRoot` again elsewhere.
- `src/consumer/consumer.module.ts` / `consumer.service.ts` — `handleAccountCreated` (idempotent — checks for an existing `User` row before creating, since RabbitMQ redelivery is expected) and `handleFailedAccountCreated` (terminal handler for the DLQ — currently just logs, see `PROBLEMS.md`).
- `src/outboxEvent/` — the Outbox pattern, same as `auth-service`: `OutboxEventService.create(userId, type, payload, tx)` writes a row to `outbox_events`, and `OutboxRelayService` (`@Interval(2000)`) publishes pending rows to `user.events` with the event type as the routing key. Used by `user.service.ts` for `user.profile.updated` (only when `displayName` actually changes) and `user.avatar.updated` (avatar upload, replace and delete; not cover).
- `src/prisma/prisma.service.ts` — uses `@prisma/adapter-pg` (the driver-adapter style client), not the classic Prisma engine binary — keep this in mind if you're debugging connection behavior, it differs from a default Prisma setup.

## Conventions specific to this service

- **ESM imports use explicit `.js` extensions** even in `.ts` files — same as `auth-service`, required by `"type": "module"`, not a mistake.
- **Never hand-edit `src/generated/prisma/`** — regenerate via `npx prisma generate`.
- **`handleAccountCreated` must stay idempotent.** RabbitMQ redelivers on consumer crash/Nack; the existing-row check at the top of the handler is what makes that safe. If you add more logic to this handler, preserve that check (or move to a proper upsert) rather than assuming each message arrives exactly once.
- **Retry/DLQ wiring lives in `app-rabbitmq.module.ts`, not in application code.** The retry delay (`x-message-ttl: 5000`) and `MAX_RETRIES = 3` (in `consumer.service.ts`) work together — don't change one without checking the other, and note the `x-death` header counting logic in `handleAccountCreated` only counts dead-letter hops from *this* queue (see its inline comment) — a naive read of `x-death` would double-count once messages start bouncing between main and retry queues.
- **Write endpoints use the same `X-User-Id`-from-gateway trust pattern as `getMe`, plus DTO validation** (the global `ValidationPipe` in `main.ts` is already configured with `whitelist`/`forbidNonWhitelisted`/`transform` — just add the DTO). A user can only ever change their own profile.
- **Anything another service keeps a copy of must be announced through the outbox, in the same transaction.** When a write changes `displayName` or the avatar, call `outboxEvent.create(..., tx)` with the `tx` of the interactive `$transaction` — not `this.prisma`, which would run outside the transaction and lose the guarantee. Keep payloads minimal (`{ userId, displayName }`, `{ userId, avatarUrl }`) and update the event table in root `CLAUDE.md` when you change one.
- **`displayName` is unique** (`UserProfile` here and `Account` in `auth-service`). A duplicate in `PATCH /me` surfaces as a Prisma `P2002` and is mapped to `409` in `updateProfile`.

## Where to look before changing things

- What fields a new account arrives with → `consumer.service.ts`'s `AccountCreatedPayload` interface, but cross-check against **both** of `auth-service`'s actual publish call sites before trusting it fully (see root `PROBLEMS.md` item 5 — the interface currently claims fields as required that aren't always sent).
- Retry/failure behavior for event processing → `app-rabbitmq.module.ts` (topology) + `consumer.service.ts` (handler logic) together, not either alone.
- What a published event carries and who consumes it → the event table in root `CLAUDE.md` (contract #3), then the call sites in `user.service.ts`.
- Current known issues → `PROBLEMS.md` in this directory.
