package bootstrap

import (
	"github.com/fizzisme/api-gateway/internal/config"
	"github.com/fizzisme/api-gateway/internal/proxy"
	"github.com/fizzisme/api-gateway/internal/ratelimit"
	"github.com/fizzisme/api-gateway/internal/resilience"
)

func RegisterRoutes(
    cfg *config.Config,
    registry *proxy.Registry,
) error {

    for _, r := range cfg.RoutesConfig {

        breaker := resilience.NewBreaker(r.Name)

        retry := &resilience.RetryConfig{
            MaxAttempts: r.Retry.Attempts,
            BaseDelay:   r.Retry.BaseDelay,
            MaxDelay:    r.Retry.MaxDelay,
        }

        p, err := proxy.New(
            r.Upstream,
            r.Prefix,
            r.Timeout,
            breaker,
            retry,
            r.Name,
        )

        if err != nil {
            return err
        }

        err = registry.Register(proxy.Route{
            Name: r.Name,
            Prefix: r.Prefix,
            Proxy: p,

            Auth: r.AuthMode,

            RateLimit: &ratelimit.RateLimitConfig{
                Requests: r.RateLimit.Requests,
                Window:   r.RateLimit.Window,
                Burst:    r.RateLimit.Burst,
            },
        })

        if err != nil {
            return err
        }
    }

    return nil
}