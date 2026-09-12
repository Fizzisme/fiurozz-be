package proxy

import (
	"context"
	"errors"
	"net/http"
	"net/http/httputil"
	"net/url"
	"strings"
	"time"
	"github.com/fizzisme/api-gateway/internal/logger"
	"go.uber.org/zap"
	"github.com/sony/gobreaker/v2"
	"github.com/fizzisme/api-gateway/internal/resilience"
	"github.com/fizzisme/api-gateway/internal/constants"
	"go.opentelemetry.io/otel"
    "go.opentelemetry.io/otel/propagation"
)

// ReverseProxy wraps httputil.ReverseProxy for a single backend
// service, stripping a route prefix before forwarding requests and
// enforcing a per-request timeout on top of circuit breaking/retry.
type ReverseProxy struct {
	targetURL *url.URL
	proxy     *httputil.ReverseProxy
	timeout   time.Duration
}

// New creates a ReverseProxy that forwards requests to target, after
// stripping prefix from the incoming request path. Outbound calls go
// through a circuit breaker + retry policy (see resilience.Builder),
// and each request is bounded by timeout (see ServeHTTP).
func New(target string,
	prefix string,
	timeout time.Duration,
	breaker *gobreaker.CircuitBreaker[*http.Response],
	retryCfg *resilience.RetryConfig,
	service string,
) (*ReverseProxy, error) {

	u, err := url.Parse(target)
	if err != nil {
		return nil, err
	}

 	// Build a bare ReverseProxy and configure routing via Rewrite
 	// instead of NewSingleHostReverseProxy + Director. Rewrite is the
 	// modern replacement: it separates the original inbound request
 	// (pr.In, read-only) from the outbound request being built
 	// (pr.Out, mutable), which avoids accidentally mutating client
 	// request state and removes the need to manually chain a
 	// "default director" before applying our own changes.
    rp := &httputil.ReverseProxy{}

	// Wrap the transport with circuit breaking and retry logic so
	// failing/unhealthy backends don't get hammered with requests.
	rp.Transport = resilience.
					NewBuilder().
					WithBreaker(breaker, service).
					WithRetry(retryCfg, service).
					Build()


    rp.Rewrite = func(pr *httputil.ProxyRequest) {

        // Sets pr.Out's scheme/host and joins its path with u's path,
    	// equivalent to what the old Director + NewSingleHostReverseProxy
    	// combo did automatically.
        pr.SetURL(u)

        // Sets X-Forwarded-For / X-Forwarded-Host / X-Forwarded-Proto
       	// on pr.Out based on pr.In, so the backend knows the original
       	// client IP and host even though the gateway is the direct caller.
        pr.SetXForwarded()

        // Strip the gateway-facing prefix so the backend sees paths
    	// relative to its own root (e.g. "/auth/login" -> "/login").
        pr.Out.URL.Path = strings.TrimPrefix(
            pr.Out.URL.Path,
            prefix,
        )

        // Drop the client's original Authorization header (raw JWT).
    	// The backend should trust the gateway-verified identity
    	// headers below instead of re-parsing the token itself.
        pr.Out.Header.Del("Authorization")

        // Forward identity/context set by upstream gateway middleware
        // (JWTAuth, RequestID) from the inbound request to the outbound
        // one, since pr.Out starts as a fresh clone and does not carry
        // these over automatically.
        pr.Out.Header.Set(
            constants.HeaderUserID,
            pr.In.Header.Get(constants.HeaderUserID),
        )

        pr.Out.Header.Set(
            constants.HeaderUserEmail,
            pr.In.Header.Get(constants.HeaderUserEmail),
        )

        pr.Out.Header.Set(
            constants.HeaderUserRoles,
            pr.In.Header.Get(constants.HeaderUserRoles),
        )

        pr.Out.Header.Set(
            constants.HeaderRequestID,
            pr.In.Header.Get(constants.HeaderRequestID),
        )

		// Propagate OpenTelemetry trace context to downstream service.
		otel.GetTextMapPropagator().Inject(
			pr.Out.Context(),
			propagation.HeaderCarrier(pr.Out.Header),
		)

		logger.Log.Info(
			"traceparent",
			zap.String("value", pr.Out.Header.Get("traceparent")),
		)
    }

	// Return a generic 502 to the client on backend failures
	// (connection refused, timeout, etc.) instead of leaking
	// internal error details.
	// Return a generic 504 to timeout requests
	rp.ErrorHandler = func(
		w http.ResponseWriter,
		r *http.Request,
		err error,
	) {

		logger.Log.Error(
			"proxy error",
			zap.Error(err),
		)

		if errors.Is(err, context.DeadlineExceeded) {
			http.Error(
				w,
				"Gateway timeout",
				http.StatusGatewayTimeout,
			)
			return
		}

		http.Error(
			w,
			"Bad Gateway",
			http.StatusBadGateway,
		)
	}

	// Log response
	rp.ModifyResponse = func(resp *http.Response) error {

		logger.Log.Info(
			"proxy response",
			zap.Int("status", resp.StatusCode),
		)

		return nil
	}

	return &ReverseProxy{
		targetURL: u,
		proxy:     rp,
		timeout: timeout,
	}, nil
}

// ReverseProxy implement http.Handler
func (p *ReverseProxy) ServeHTTP(
	w http.ResponseWriter,
	r *http.Request,
) {

	if p.timeout <= 0 {
		p.proxy.ServeHTTP(w,r)
		return
	}

	ctx, cancel := context.WithTimeout(
		r.Context(),
		p.timeout,
	)

	defer cancel()

	newReq := r.Clone(ctx)

	p.proxy.ServeHTTP(w, newReq)
}