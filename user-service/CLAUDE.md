# CLAUDE.md — user-service

Service-specific context. Read the root [`../CLAUDE.md`](../CLAUDE.md) first — it covers the identity-header contract and the `account.created` event this service consumes.

## Purpose

Owns user-facing profile data (profile, settings, social links). Deliberately doesn't own account/credential data — that's `auth-service`. The two are kept eventually-consistent via one event (`account.created`), not a synchronous call, so `user-service` can be down without blocking registration, and vice versa.

## Code map

- `src/user/user.controller.ts` / `user.service.ts` — the only HTTP surface today, `GET /me`. Trusts `X-User-Id` completely (see root `CLAUDE.md` contract #2) — the guard in `user.service.ts#getMe` that throws if the header is missing is defense-in-depth, not the actual auth check (that already happened at the gateway, when it runs — see this service's `PROBLEMS.md` for a config gap here).
- `src/consumer/consumer.module.ts` — declares the full RabbitMQ topology this service depends on: main exchange `user.events`, a retry exchange (`user.events.retry`, TTL-based backoff via `x-message-ttl`), and a dead-letter exchange (`user.events.dlx`) for messages that exhaust retries.
- `src/consumer/consumer.service.ts` — `handleAccountCreated` (idempotent — checks for an existing `User` row before creating, since RabbitMQ redelivery is expected) and `handleFailedAccountCreated` (terminal handler for the DLQ — currently just logs, see `PROBLEMS.md`).
- `src/prisma/prisma.service.ts` — uses `@prisma/adapter-pg` (the driver-adapter style client), not the classic Prisma engine binary — keep this in mind if you're debugging connection behavior, it differs from a default Prisma setup.

## Conventions specific to this service

- **ESM imports use explicit `.js` extensions** even in `.ts` files — same as `auth-service`, required by `"type": "module"`, not a mistake.
- **Never hand-edit `src/generated/prisma/`** — regenerate via `npx prisma generate`.
- **`handleAccountCreated` must stay idempotent.** RabbitMQ redelivers on consumer crash/Nack; the existing-row check at the top of the handler is what makes that safe. If you add more logic to this handler, preserve that check (or move to a proper upsert) rather than assuming each message arrives exactly once.
- **Retry/DLQ wiring lives in `consumer.module.ts`, not in application code.** The retry delay (`x-message-ttl: 5000`) and `MAX_RETRIES = 3` (in `consumer.service.ts`) work together — don't change one without checking the other, and note the `x-death` header counting logic in `handleAccountCreated` only counts dead-letter hops from *this* queue (see its inline comment) — a naive read of `x-death` would double-count once messages start bouncing between main and retry queues.
- **This service currently has no write endpoints.** If you add one (update profile, settings, social links), it needs the same `X-User-Id`-from-gateway trust pattern as `getMe`, plus DTO validation (the global `ValidationPipe` in `main.ts` is already configured with `whitelist`/`forbidNonWhitelisted`/`transform` — just add the DTO).

## Where to look before changing things

- What fields a new account arrives with → `consumer.service.ts`'s `AccountCreatedPayload` interface, but cross-check against **both** of `auth-service`'s actual publish call sites before trusting it fully (see root `PROBLEMS.md` item 5 — the interface currently claims fields as required that aren't always sent).
- Retry/failure behavior for event processing → `consumer.module.ts` (topology) + `consumer.service.ts` (handler logic) together, not either alone.
- Current known issues → `PROBLEMS.md` in this directory.
