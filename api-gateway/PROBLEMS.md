# Known issues — api-gateway

Reviewed 2026-08-30 against `main`, by re-running the checks from the 2026-07-23 [`CODE_REVIEW.md`](CODE_REVIEW.md) audit. That document is kept for historical detail (evidence, line numbers, references) — this file tracks **current** status only. Update this file (not `CODE_REVIEW.md`) whenever something here is fixed.

## Already fixed since the 2026-07-23 audit

Verified directly against the current code — listed here so nobody re-reports them as new:

| Old ID | Was | Now |
| --- | --- | --- |
| AGW-001 | `configs/.env` tracked in Git | `git ls-files configs/.env` returns nothing — untracked |
| AGW-002 | Access token logged via `fmt.Println` | No such call remains in `internal/middleware/jwt.go` |
| AGW-003 | Rate limit ran before JWT; client could spoof `X-User-ID` | `internal/router/builder.go` now runs `JWTAuth` before `RateLimit`; `BuildRateLimitKey` (`internal/helper/rate_limit.go`) keys off verified claims, not headers |
| AGW-006 | JWT accepted any HMAC method, no required `exp`, manual issuer check | `internal/auth/jwt.go` now uses `jwt.WithValidMethods([]string{"HS256"})`, `jwt.WithExpirationRequired()`, `jwt.WithIssuer(...)` |
| AGW-007 | Reverse proxy used deprecated `Director` | `internal/proxy/reverse_proxy.go` uses `httputil.ProxyRequest.Rewrite` + `SetURL`/`SetXForwarded` |
| AGW-008 | No trusted-proxy configuration for Gin | `internal/router/router.go` calls `r.SetTrustedProxies(nil)` |
| AGW-013 | No global trace propagator; outbound calls not instrumented | `internal/tracing/tracer.go` sets a composite `TraceContext + Baggage` propagator; `internal/proxy/reverse_proxy.go`'s `Rewrite` injects it into outbound headers |

## Still open (verified 2026-08-30 by running the commands below)

### AGW-004 — Proxy package tests don't compile

```
go vet ./...
# internal\proxy\reverse_proxy_test.go:44: not enough arguments in call to New
#   have (string, string, time.Duration)
#   want (string, string, time.Duration, *gobreaker.CircuitBreaker[*http.Response], *resilience.RetryConfig, string)
```

`New()`'s signature grew (breaker, retry config, service name) after these tests were written; all 5 call sites in `reverse_proxy_test.go` still use the 3-argument form. `go test ./...` fails to build the `internal/proxy` package as a result — regressions in prefix stripping, header forwarding, and timeout behavior currently have zero test coverage protecting them.

**Fix:** update the 5 `New(...)` calls to pass a breaker/retry config (a test helper that builds sane defaults would avoid repeating this at each call site).

### AGW-005 — Resilience tests panic (global metrics not initialized)

```
go test ./internal/resilience
# panic: runtime error: invalid memory address or nil pointer dereference
#   internal/resilience.NewBreaker.func2 → metrics.BreakerState.WithLabelValues(...)
```

`internal/resilience/breaker.go`'s `OnStateChange` callback references the package-level `metrics.BreakerState` / `metrics.BreakerStateChangeTotal` collectors, which are only non-nil after `metrics.Init()` runs (done in `main.go`, not in the test). `manager_test.go`'s `TestBreaker_OpenAfterFailures` trips the breaker to `Open` without calling `metrics.Init()` first, causing a nil-pointer panic on a `*prometheus.GaugeVec` that was never constructed.

**Fix:** either call `metrics.Init()` in a `TestMain` for this package, or (better long-term) inject a metrics recorder into `resilience` instead of depending on package-level globals, so the package is usable/testable without a hidden init-order requirement.

### AGW-009 — Config requires `.env` to exist — FIXED 2026-09-14 (partial)

`godotenv.Load("configs/.env")` no longer returns a fatal error when the file is absent (`os.IsNotExist` is tolerated), so a container relying purely on injected env vars (Docker Compose `env_file`/`environment`) now boots. Field validation (non-empty/well-formed `PORT`, `JWT_SECRET`, upstream URLs, etc.) is still not implemented — that half of this item remains open.

### AGW-010 — Fresh checkout won't start without a manually created `logs/` directory — FIXED 2026-09-14 (native dev still needs the manual step)

`Dockerfile` now creates `logs/` (owned by the non-root `gateway` user) as part of the image build, so the containerized gateway no longer needs this done by hand. Running natively (`go run ./cmd/server`) still requires creating `logs/` first, as documented in the README.

### AGW-011 — Circuit breaker doesn't count upstream 5xx responses as failures

`internal/resilience/retry_transport.go` retries on `500/502/503/504`, but `internal/resilience/breaker_transport.go` (`RoundTrip`) returns `(*http.Response, nil)` for those same responses — `gobreaker` only counts a call as a failure when the wrapped function returns a non-nil `error`, so an upstream that returns nothing but 5xx never trips the breaker. The breaker only reacts to transport-level errors (connection refused, timeout), not application-level failures.

**Fix:** decide which status codes should count as breaker failures, and have the transport return an error for them (after still returning/logging the actual response as needed) so `gobreaker`'s failure counting reflects real upstream health.

### AGW-012 — Structured request logs never contain the authenticated user ID

`internal/middleware/logger.go` is registered as **global** middleware (`internal/router/router.go`, before route registration) and reads claims from the Gin context at that point — but `JWTAuth` (which sets those claims) only runs later, as **per-route** middleware (`internal/router/builder.go`). So `Logger` always runs before any claims exist, and the `user_id` field it tries to log is always empty for every request, authenticated or not.

**Fix:** read claims after `c.Next()` (post-handler) instead of before, or split logging into a pre-request line (method/path/request ID) and a post-request line (status/duration/user ID).

### AGW-014 — Local observability stack uses EOL/unpinned images (networking half fixed 2026-09-14)

`deploy/docker-compose.yml` (host-network, host-mounted logs, disconnected from the app's own Docker network) has been retired; Prometheus, Grafana, Jaeger, Loki and Promtail now live in `infrastructure/compose.yaml` on the same `fiurozz` Compose project as the services — normal bridge networking (no `network_mode: host`), container-name-based Prometheus scrape target, and a named volume (`gateway-logs`) instead of a host bind-mount for the log shipper. This also resolves root `PROBLEMS.md` item 7 (Zipkin, which none of the services actually speak, is replaced by Jaeger's OTLP receiver). Still using `prom/prometheus:latest`, `grafana/grafana:latest`, `jaegertracing/all-in-one:1.71.0` (Jaeger v1, EOL), and `grafana/promtail:3.0.0` (EOL) — see `CODE_REVIEW.md#AGW-014` and `UPGRADE.md` phase 3 for the still-open image-upgrade plan.

### AGW-015 — Formatting and module metadata still not clean

```
gofmt -l .          # lists all 37 Go files
go mod tidy -diff   # shows github.com/gin-contrib/cors mismarked "// indirect" in go.mod, despite being imported directly in internal/router/router.go
```

**Fix:** run `gofmt -w .` and `go mod tidy`, review the diff, and add a CI step (see root `PROBLEMS.md` item 10) that fails on either drifting again.

### Backlog (unchanged from the original audit, still accurate)

- Only `auth` and `user` routes are registered; `PROJECT_SERVICE`/`MEMBER_SERVICE`/`CHAT_SERVICE`/`NOTIFICATION_SERVICE` env vars are loaded but unused.
- `/health` is liveness-only; no `/ready` endpoint checks config or dependency health.
- Rate limiter is in-memory per instance — see root `PROBLEMS.md` item 8 (Redis is provisioned in shared infra but unused).
- `/metrics` has no auth/network restriction.
- CORS origins (`internal/router/router.go`) are hard-coded to `http://localhost:3000` — fine for local dev, needs to be environment-configurable before any non-local deployment.
