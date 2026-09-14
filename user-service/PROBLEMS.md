# Known issues — user-service

Reviewed 2026-08-30 against `main`. Cross-service issues (RabbitMQ provisioning, env var naming, shared JWT secret, the `account.created` schema mismatch) are tracked in the root [`PROBLEMS.md`](../PROBLEMS.md) — this file covers issues local to this service's own code.

## The service is read-only despite a data model built for mutation

The only HTTP endpoint is `GET /me` (`src/user/user.controller.ts`). There is no endpoint to update profile fields, change settings, or add/edit/reorder social links, even though `prisma/schema.prisma` clearly models all of that (`UserProfile`'s editable fields, `UserSetting`'s toggles, `SocialLink`'s `order` field for reordering). Right now a `UserProfile` is created once, empty, by the `account.created` consumer, and nothing in this service can ever change it afterward.

**Impact:** not a bug, but likely the single biggest functional gap if the intent is a usable profile feature rather than just the event-consumption plumbing — worth flagging so it isn't mistaken for "done."

## `auth_mode: optional` on `/api/users` doesn't match what this service's only endpoint needs

`api-gateway/configs/routes.yaml` registers the `/api/users` prefix with `auth_mode: optional`, meaning the gateway will proxy a request through **without** a valid token. But `GET /me` (this service's only endpoint) has no meaning without an authenticated identity — `user.service.ts#getMe` immediately throws `UnauthorizedException` if `X-User-Id` is missing, with a comment noting this "should be unreachable in practice" because the gateway's `AuthMode` is assumed to guarantee the header is present.

That assumption doesn't hold today: with `auth_mode: optional`, an unauthenticated request to `/api/users/me` **is** reachable in practice, it just gets rejected one layer further in than intended (inside this service, via a thrown exception, rather than at the gateway with a clean 401).

**Impact:** low right now (the net result is still a 401), but the moment a public/optional-auth endpoint is added to this service alongside `/me` (see the previous item — any new write endpoint will likely also require auth), the shared route-level `auth_mode` stops being able to express "some endpoints here are public, some aren't" — every endpoint under `/api/users` gets the same policy.

**Fix:** change `/api/users` to `auth_mode: required` in `api-gateway/configs/routes.yaml` now, since every current endpoint needs it; if a public endpoint is added later, that's the point to introduce per-endpoint (not just per-route-prefix) auth policy in the gateway.

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
