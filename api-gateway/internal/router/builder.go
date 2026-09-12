package router

import (
	"github.com/fizzisme/api-gateway/internal/auth"
	"github.com/fizzisme/api-gateway/internal/middleware"
	"github.com/fizzisme/api-gateway/internal/proxy"
	"github.com/fizzisme/api-gateway/internal/ratelimit"
	"github.com/gin-gonic/gin"
	"golang.org/x/time/rate"
)

// buildHandlers assembles the middleware chain for a single route,
// in order: rate limiting -> JWT authentication -> role authorization
// -> the backend reverse proxy. Middleware is only added when the
// route config requires it (e.g. public routes skip auth).
func buildHandlers(
	route proxy.Route,
	jwtService *auth.JWTService,
	manager *ratelimit.Manager,
) []gin.HandlerFunc {

	handlers := make([]gin.HandlerFunc, 0)

	handlers = append(
    		handlers,
    		middleware.JWTAuth(jwtService, route.Auth),
    )

	if route.RateLimit != nil {
		cfg := route.RateLimit

		// Convert the configured "N requests per window" into the
		// steady-state rate.Limit (requests/sec) expected by
		// golang.org/x/time/rate.
		handlers = append(
			handlers,
			middleware.RateLimit(
				manager,
				rate.Limit(float64(cfg.Requests)/cfg.Window.Seconds()),
				cfg.Burst,
			),
		)
	}

	// Final handler: forward the request to the backend service.
	handlers = append(
		handlers,
		gin.WrapH(route.Proxy),
	)

	return handlers
}