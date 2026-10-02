package router

import (
	"github.com/fizzisme/api-gateway/internal/auth"
	"github.com/fizzisme/api-gateway/internal/proxy"
	"github.com/gin-gonic/gin"
	"github.com/fizzisme/api-gateway/internal/ratelimit"
)

func registerProxyRoutes(
	r *gin.Engine,
	registry *proxy.Registry,
	jwtService *auth.JWTService,
	manager *ratelimit.Manager,
) {

	for _, route := range registry.Routes() {

		handlers := buildHandlers(
			route,
			jwtService,
			manager,
		)

		// Exact prefix too: "/api/projects" alone would not match
		// "/api/projects/*path", and Gin would answer 307.
		r.Any(
			route.Prefix,
			handlers...,
		)

		r.Any(
			route.Prefix+"/*path",
			handlers...,
		)

	}

}