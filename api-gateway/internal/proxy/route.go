package proxy

import (
	"time"

	"github.com/fizzisme/api-gateway/internal/config"
	"github.com/fizzisme/api-gateway/internal/ratelimit"
)

type Route struct {
	Name string

	// URL public
	Prefix string

	Proxy *ReverseProxy

	RateLimit *ratelimit.RateLimitConfig

	Burst int

	Timeout time.Duration

	Auth config.AuthMode
}