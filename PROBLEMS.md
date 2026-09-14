# Architecture-level problems

Cross-cutting issues that span more than one service. Issues local to a single service (e.g. a specific gateway middleware bug) live in that service's own `PROBLEMS.md` — see `api-gateway/PROBLEMS.md`, `auth-service/PROBLEMS.md`, `user-service/PROBLEMS.md`.

Reviewed: 2026-08-30, against the code on `main` (excludes `project-service`, developed independently).

## 1. RabbitMQ is required by two services but provisioned nowhere

`auth-service` (Outbox relay) and `user-service` (event consumer) both hard-depend on RabbitMQ for the `account.created` flow — registration silently stops short of creating a user profile if it's missing. But no compose file in the repo defines a RabbitMQ container: `infrastructure/compose.yaml` (the shared local-dev infra) only has Redis and Zipkin, and neither service ships its own broker.

**Impact:** a fresh clone following the root `README.md` setup steps has no working message broker; both services fall back to their hard-coded default (`amqp://guest:guest@localhost:5672`), which connects to nothing unless a developer happens to start RabbitMQ manually.

**Fix:** add a `rabbitmq` service (with the management plugin, for visibility into queues/DLQs) to `infrastructure/compose.yaml`, and document it in the root README's getting-started steps.

## 2. Inconsistent env var name for the same RabbitMQ connection

`auth-service/src/outboxEvent/outbox-event.module.ts` reads `RABBIT_MQ_URI`; `user-service/src/consumer/consumer.module.ts` reads `RABBITMQ_URL`. Both connect to the *same* broker and exchange (`user.events`), but under two different variable names.

**Impact:** easy to set one and forget the other when configuring a new environment; no shared config source would even catch the mismatch since each service loads its own `.env` independently.

**Fix:** standardize on one name (e.g. `RABBITMQ_URL`) across both services.

## 3. RabbitMQ variable undocumented in either service's `.env.example`

Neither `auth-service/.env.example` nor `user-service/.env.example` lists the RabbitMQ URI variable at all (see problem 2 for the actual names used in code). Someone provisioning a new environment from the example file alone has no indication the variable exists or is required.

**Fix:** add the variable (with its correct name per service, or the unified name once problem 2 is fixed) to both `.env.example` files.

## 4. Shared JWT secret has no enforced or documented single source of truth

`api-gateway` verifies access tokens with `JWT_SECRET`/`JWT_ISSUER`; `auth-service` signs them with `JWT_ACCESS_SECRET`/`JWT_ISSUER`. These must be byte-for-byte identical (secret) and identical (issuer) for any authenticated request to work, but:

- They live in two unrelated `.env` files with two different variable names.
- Nothing validates at startup that a configured secret is non-empty or "looks right" — a mismatch fails silently, request by request, as every JWT verification returns invalid.

**Impact:** onboarding a new environment (or rotating the secret) requires updating two files by hand with no cross-check; a mismatch produces confusing "every request unauthorized" symptoms with no direct error pointing at the cause.

**Fix (low effort now, without a secret manager):** document the shared-secret requirement prominently in both services' README/`.env.example` comments (already done in this pass), and consider a startup smoke check (e.g. gateway signs a throwaway token and verifies it can also decode a token freshly issued by hitting auth-service's `/login`, or a shared constant embedded in both `.env.example` files with a matching hash comment). Longer term: move to asymmetric signing (`RS256`) with auth-service holding the private key and the gateway fetching/caching the public key or JWKS, so there's nothing shared to keep in sync.

## 5. `account.created` event contract is duplicated and inconsistent, with no schema validation

`auth-service` publishes `account.created` from two different call sites with two different field sets:

- Password registration (`auth.service.ts#register`): `id, email, fullName, displayName, country, birthday, gender` — no `avatarUrl`.
- OAuth signup (`oauth-account.service.ts#loginWithOauth`): `id, email, fullName, displayName, avatarUrl` — no `country`, `birthday`, or `gender`.

`user-service`'s consumer (`consumer.service.ts`) declares one `AccountCreatedPayload` interface where **all** of those fields are required (non-optional), and both producers technically violate it — TypeScript doesn't catch this because the payload is untyped RabbitMQ JSON at the actual boundary; the interface only documents an assumption.

**Impact:** any consumer code that reads `payload.avatarUrl` for a password-registered user (or `payload.gender`/`payload.birthday`/`payload.country` for an OAuth user) gets `undefined` at runtime with no warning, not a type error.

**Fix:** define the event payload as a single shared shape (a small shared package, or at minimum a comment block copied verbatim into both files with a "keep these two in sync" note), make truly-optional fields (`avatarUrl`, `country`, `birthday`, `gender`) optional in the interface, and add runtime validation (e.g. `class-validator`/`zod`) on the consumer side so a malformed event fails loudly instead of silently producing partial data.

## 6. Gateway → service trust boundary is convention-only

Both `auth-service` (session endpoints) and `user-service` (`getMe`) read `X-User-Id` directly off the request and trust it completely, with comments stating the assumption explicitly ("this service only accepts traffic from the Gateway"). Nothing in the repo enforces that assumption:

- No network policy/firewall config restricting who can reach `auth-service`/`user-service` directly.
- No shared secret or mTLS between the gateway and the internal services to prove a request actually passed through it.
- In local dev, both services bind to a normal host port and are directly reachable, bypassing the gateway entirely.

**Impact:** in any environment where the internal services are reachable without going through the gateway (a misconfigured network, a docker-compose that exposes ports, a debugging session left running), anyone can impersonate any user by setting `X-User-Id` directly.

**Fix:** at minimum, add a shared internal-only header/secret the gateway attaches and each service validates (simple HMAC or static shared secret is enough for a showcase project); properly, put internal services on a private network/segment not reachable from outside the gateway.

## 7. Observability stack doesn't match what the services actually export

- `infrastructure/compose.yaml` (the "shared infra" every service is meant to use) provisions **Redis and Zipkin only**.
- All three reviewed services (`api-gateway`, `auth-service`, `user-service`) export traces via **OTLP gRPC** to `localhost:4317` by default — a protocol Zipkin's container doesn't speak, and a port nothing in `infrastructure/compose.yaml` listens on.
- A working OTLP receiver (Jaeger or an OpenTelemetry Collector) only exists in `api-gateway/deploy/docker-compose.yml`, a gateway-local dev stack not referenced from the root compose or from either NestJS service's docs.

**Impact:** running `docker compose -f infrastructure/compose.yaml up -d` as the root README currently instructs gives you a Zipkin UI that will never receive a trace, while all three services log tracing-export connection failures unless someone separately starts the gateway's own observability stack.

**Fix:** pick one exporter target for the whole system (OTLP is already what every service defaults to) and add a real OTLP receiver (Jaeger v2 or an OTel Collector) to `infrastructure/compose.yaml`; remove or repurpose the unused Zipkin container.

## 8. Redis is provisioned but unused

`infrastructure/compose.yaml` runs Redis, but no service in this review references it. The one place an external store would matter — `api-gateway`'s rate limiter (`internal/ratelimit/manager.go`) — is implemented as a plain in-memory `map`, meaning rate limits reset per gateway instance and won't be consistent if the gateway is ever scaled beyond one replica.

**Fix:** either wire the gateway's rate limiter to Redis (needed before running more than one gateway instance) or drop the unused Redis service until something needs it, to avoid implying infrastructure that isn't actually load-bearing.

## 9. No containerized way to run the auth+user+gateway slice

`project-service` has a `Dockerfile` and its own `compose.yaml`, wired into the root `compose.yaml` via `include:`. `api-gateway`, `auth-service`, and `user-service` have **no Dockerfile and no compose entry** — the only way to run them today is `go run ./cmd/server` / `npm run start:dev` directly on the host, with no compose file referencing any of the three by name.

**Impact:** the root `compose.yaml` (`docker compose up`) starts shared infra plus `project-service` only — someone expecting "the backend" to come up with one command gets a partial system with no indication the other three services need to be started separately by hand.

**Fix:** add a `Dockerfile` to each of the three services and register them in `infrastructure/compose.yaml` (or a new `compose.yaml` at each service root, included from the top, matching the `project-service` pattern).

## 10. No CI

There is no `.github/workflows` (or any other CI config) anywhere in the repository. Nothing automatically runs tests, linting, `go vet`/`gofmt`, or `docker compose config` validation before a PR merges — the only automated check-like process visible is the `api-gateway/CODE_REVIEW.md` manual audit from 2026-07-23.

**Fix:** add a minimal CI workflow per service (build + lint + test), even before test coverage is meaningful (see problem 11) — it establishes the gate that would have caught, e.g., problem 2's naming inconsistency at review time.

## 11. Zero automated test coverage in the two NestJS services

Both `auth-service` and `user-service` have Jest configured (`package.json` `test`/`test:e2e` scripts) but neither has a `test/` directory or any `*.spec.ts` file. `api-gateway` has some Go tests, but as of the last audit they didn't fully compile/pass — see `api-gateway/PROBLEMS.md` for current status.

**Fix:** start with the highest-value paths per service — token issuance/verification and the outbox relay for `auth-service`; the `account.created` consumer's idempotency and retry/DLQ logic for `user-service` — since those are exactly the places where a silent regression would be hardest to notice manually.

## How to use this document

When you fix one of these, update this file (mark it resolved with a one-line note and date, or delete the entry if it's fully gone) rather than leaving it to go stale — this file is only useful if it reflects the code, not a point-in-time snapshot. The same applies to each service's own `PROBLEMS.md`.
