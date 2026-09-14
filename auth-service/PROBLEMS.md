# Known issues — auth-service

Reviewed 2026-08-30 against `main`. Cross-service issues (RabbitMQ provisioning, env var naming, shared JWT secret, the `account.created` schema mismatch) are tracked in the root [`PROBLEMS.md`](../PROBLEMS.md) — this file covers issues local to this service's own code.

## OAuth handoff code falls back to a hard-coded, publicly-known secret

`src/auth/token/token.service.ts`, both `createOAuthHandoffCode` and `verifyOAuthHandoffCode`:

```ts
secret: process.env.OAUTH_HANDOFF_SECRET ?? 'OAUTH_HANDOFF_SECRET',
```

The design intent, per the comment directly above it, is that the handoff code is "signed with a SEPARATE secret from access/refresh tokens, so even if this secret leaked, it couldn't be used to forge a real access token." But if `OAUTH_HANDOFF_SECRET` is unset, both signing and verification silently fall back to the literal string `'OAUTH_HANDOFF_SECRET'` — a secret anyone can read directly from this file. Since the handoff code only carries a 30-second TTL and an `accountId`, exploiting this specifically requires a narrow timing window, but it still defeats the "separate secret" design goal outright and provides no signal that the environment is misconfigured.

**Fix:** fail fast at startup (or on first use) if `OAUTH_HANDOFF_SECRET` is unset, instead of silently using a guessable default.

## `revoked` / `revokedAt` columns are dead — refresh() checks a flag nothing ever sets

The `RefreshToken` model has `revoked: Boolean @default(false)` and `revokedAt: DateTime?`. `refresh-token.service.ts` filters `getSessions` by `revoked: false`, and `auth.service.ts#refresh` checks `if (!token || token.revoked || ...)`. But every revocation path in this codebase — `deleteByJti`, `deleteSession`, `deleteAllSessions` — **hard-deletes** the row instead of setting `revoked: true`. No code path ever sets `revoked` to `true`, so:

- The `token.revoked` check in `refresh()` is unreachable dead code (a revoked session's row doesn't exist to be found by `findByJti` in the first place — its absence, not the flag, is what stops refresh from succeeding).
- `getSessions`'s `revoked: false` filter is a no-op — every remaining row already satisfies it.

**Impact:** low on its own (delete-based revocation still works correctly), but the schema and the `refresh()` logic imply a soft-delete/audit-trail design that doesn't actually exist — anyone reading `refresh()` in isolation would reasonably expect revoked-but-present sessions to be distinguishable from deleted ones, and they aren't.

**Fix:** either implement soft-delete properly (set `revoked`/`revokedAt` instead of deleting, and filter them out everywhere including `findByJti`), or drop the two columns and the dead `token.revoked` check to match what the code actually does.

## `RegisterDto`'s gender enum is narrower than `user-service`'s

`src/auth/dto/register.dto.ts`:

```ts
@IsEnum(['MALE', 'FEMALE', 'UNKNOWN'])
gender: string;
```

`user-service/prisma/schema.prisma` defines `enum Gender { UNKNOWN MALE FEMALE OTHER }` — the profile this value ultimately lands in (via the `account.created` event → `ConsumerService.handleAccountCreated`) supports a 4th value, `OTHER`, that registration can never actually send. A user who would select "Other" on a signup form is rejected by this DTO's validation before the request even reaches the account-creation logic.

**Fix:** add `'OTHER'` to the `@IsEnum([...])` list here (or, better, share one `Gender` enum definition between the two services instead of maintaining it by hand in both places — see root `PROBLEMS.md` item 5 for the same duplication problem affecting the whole event payload).

## `emailVerified` is set once and never updated — no verification flow exists

`Account.emailVerified` is written at creation time only: `false` for password registration, `!!profile.email` for OAuth (`oauth-account.service.ts`). There is no endpoint, service, or scheduled job anywhere in `src/` that ever transitions it afterward (no "send verification email," no "confirm email" endpoint). For password-registered accounts, `emailVerified` is permanently `false` unless something outside this codebase updates it directly in the database.

**Impact:** if any future feature (this service or another) gates behavior on `emailVerified`, it will incorrectly treat every password-registered account as permanently unverified. Currently nothing reads the field downstream, so there's no active bug — but the field is misleading as-is.

**Fix:** either implement the verification flow (send a signed link/code, add a `POST /verify-email` endpoint) or remove the field until it's actually wired up, so its presence doesn't imply a feature that isn't there.

## No automated tests

`package.json` has `test`/`test:e2e` scripts configured (Jest), but there is no `test/` directory and zero `*.spec.ts` files anywhere in `src/`. The riskiest untested paths, given what's covered above, are token issuance/verification (`jwt-token.service.ts`), the register→outbox→relay pipeline (crossing a DB transaction and an external RabbitMQ publish), and the OAuth account-linking logic in `oauth-account.service.ts` (the email-collision handling there has subtle `null`-vs-`undefined` semantics worth locking down with a test, per its own inline comment).

**Fix:** see root `PROBLEMS.md` item 11 for the cross-service framing; for this service specifically, start with `jwt-token.service.ts` (pure, easy to unit test) and `oauth-account.service.ts#loginWithOauth` (highest logical complexity, already has a subtle edge case documented in comments).
