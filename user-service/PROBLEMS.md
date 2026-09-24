# Known issues — user-service

Reviewed 2026-08-30 against `main`. Cross-service issues (RabbitMQ provisioning, env var naming, shared JWT secret, the `account.created` schema mismatch) are tracked in the root [`PROBLEMS.md`](../PROBLEMS.md) — this file covers issues local to this service's own code.

## The service is read-only despite a data model built for mutation

The only HTTP endpoint is `GET /me` (`src/user/user.controller.ts`). There is no endpoint to update profile fields, change settings, or add/edit/reorder social links, even though `prisma/schema.prisma` clearly models all of that (`UserProfile`'s editable fields, `UserSetting`'s toggles, `SocialLink`'s `order` field for reordering). Right now a `UserProfile` is created once, empty, by the `account.created` consumer, and nothing in this service can ever change it afterward.

**Impact:** not a bug, but likely the single biggest functional gap if the intent is a usable profile feature rather than just the event-consumption plumbing — worth flagging so it isn't mistaken for "done."

## `auth_mode: optional` on `/api/users` mixes public and authenticated endpoints under one policy

`api-gateway/configs/routes.yaml` registers the `/api/users` prefix with `auth_mode: optional`, meaning the gateway will proxy a request through **without** a valid token. Endpoints under that prefix now want two different policies:

- **Public:** `GET /` (users list) and `GET /:identifier` (public profile) — the members directory is browsable by anonymous visitors, and each optionally personalizes `isFollowing` from `X-User-Id` when a token *is* present.
- **Authenticated:** `GET /me`, `PATCH /me`, `POST|DELETE /:id/follow` — meaningless without an identity, and each rejects a missing one via the `@UserId()` decorator (`src/common/decorators/user-id.decorator.ts`), which throws `UnauthorizedException`.

`optional` is the only mode that lets the public endpoints work at all, so it is the correct setting today — but it is correct by luck of being the permissive option, not because the gateway can express this split. The authenticated endpoints get their 401 one layer further in than intended (inside this service, via a thrown exception, rather than at the gateway).

> **Superseded 2026-09-24.** This entry previously recommended switching to `auth_mode: required`, on the premise that `GET /me` was the service's only endpoint. That premise no longer holds — `required` would now break anonymous browsing of the members directory. Do not apply that older advice.

**Impact:** low. Authenticated endpoints still return 401, just from the service rather than the gateway. The real cost is that route-level `auth_mode` can't express "some endpoints here are public, some aren't", so per-endpoint auth is enforced by convention in application code instead of at the edge.

**Fix:** introduce per-endpoint (not just per-route-prefix) auth policy in the gateway, then mark the `/me` and follow-mutation endpoints `required` while leaving the list/detail endpoints `optional`. Until then, keep `optional` and keep relying on `@UserId()` to reject anonymous callers on the endpoints that need an identity.

One gateway-side caveat worth knowing if that work happens: `auth_mode: none` returns before `stripIdentityHeaders` (`api-gateway/internal/middleware/jwt.go`), so client-supplied `X-User-Id` would pass straight through to this service. Any future "truly public" mode here must use `optional`, not `none`. Also note the proxy sets identity headers unconditionally, so an anonymous request arrives with `X-User-Id` as an **empty string** rather than an absent header — see `OptionalUserId` in `src/common/decorators/user-id.decorator.ts`.

## Profile data copied from `auth-service` has no update path

`UserProfile.email`, `displayName`, and `fullName` are populated once, from the `account.created` event payload, when the profile row is first created. `auth-service` has no corresponding `account.updated` (or similar) event, and this service only ever subscribes to `account.created` (`src/consumer/consumer.module.ts` binds exactly one routing key). If an account's email or display name ever changes in `auth-service` — whether through a future "update account" feature there, or a direct DB fix — `user-service`'s copy silently goes stale with no mechanism to catch up.

**Impact:** none today (neither service has an "edit account" feature yet), but this is a concrete instance of the general event-schema gap tracked in root `PROBLEMS.md` item 5, and worth keeping in mind before adding an account-edit feature to `auth-service` without also planning how the copy here gets updated.

**Fix:** when an update path is added to `auth-service`, publish a corresponding event (`account.updated`) and add a consumer here, or reconsider whether `email`/`displayName`/`fullName` should be duplicated into `user-service` at all versus fetched from `auth-service` on demand.

## Dead-lettered events are logged and then gone

`consumer.service.ts#handleFailedAccountCreated` (the terminal handler for `user.events.dlx`) only logs an error — there's an explicit `// TODO later: persist to a dedicated table ... and send an alert` acknowledging this. Today, a registration whose profile-creation retries are all exhausted (3 attempts) leaves that account permanently without a `User`/`UserProfile` row, discoverable only by grepping application logs.

**Fix:** at minimum, persist failed payloads to a table for manual reprocessing, per the existing TODO — this is already tracked in code, repeating it here so it isn't lost among the RabbitMQ plumbing.

## No automated tests

Same as `auth-service` — `package.json` has Jest configured but there is no `test/` directory and zero `*.spec.ts` files. The highest-value target here is `handleAccountCreated`'s idempotency check and the `x-death`-based retry counting (both easy to get subtly wrong and hard to notice without a test, since they only matter on redelivery/failure paths that don't show up in a normal happy-path manual test).

**Fix:** see root `PROBLEMS.md` item 11 for the cross-service framing; for this service specifically, a test harness that can simulate RabbitMQ redelivery (or at least call `handleAccountCreated` twice with the same payload and assert no duplicate/error) would directly cover the idempotency guarantee the code relies on.

## Traces were mislabeled as `AUTH-SERVICE` — FIXED 2026-09-14

`src/tracing.ts`'s OTel resource attribute fell back to `process.env.APP_NAME ?? 'AUTH-SERVICE'` — copy-pasted from `auth-service/src/tracing.ts` without updating the fallback name. Since neither service's `.env` set `APP_NAME`, every user-service trace showed up in Jaeger under the service name "AUTH-SERVICE", indistinguishable from actual auth-service traces. Fixed the fallback to `'USER-SERVICE'`, and both services now set `APP_NAME` explicitly in `.env`/`.env.example` so this class of bug can't recur silently.
