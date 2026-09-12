package middleware

import (
	"net/http"
	"strings"

	"github.com/fizzisme/api-gateway/internal/auth"
	"github.com/fizzisme/api-gateway/internal/config"
	"github.com/fizzisme/api-gateway/internal/constants"
	"github.com/gin-gonic/gin"
)

// JWTAuth returns a middleware that authenticates requests via a
// Bearer JWT, with behavior controlled by authMode:
// - AuthNone: skip authentication entirely.
// - AuthOptional: authenticate if a token is present, but let the
// request through unauthenticated if it's missing or invalid
// (used for routes that behave differently for logged-in vs
// anonymous users, without requiring login).
// - AuthRequired: reject the request with 401 unless a valid token
// is present.
//
// On successful verification, decoded claims are stored in the Gin
// context and trusted identity headers are re-injected for downstream
// services, after stripping any client-supplied ones.
func JWTAuth(jwtService *auth.JWTService, authMode config.AuthMode) gin.HandlerFunc {
	return func(c *gin.Context) {
		authHeader := c.GetHeader("Authorization")

		switch authMode {
		case config.AuthNone:
			c.Next()
			return

		case config.AuthOptional:
			if authHeader == "" {
				stripIdentityHeaders(c)
				c.Next()
				return
			}

		case config.AuthRequired:
			if authHeader == "" {
				c.AbortWithStatusJSON(
					http.StatusUnauthorized,
					gin.H{
						"message": "Missing access token",
					},
				)
				return
			}
		}

		if !strings.HasPrefix(authHeader, "Bearer ") {
			if authMode == config.AuthOptional {
				stripIdentityHeaders(c)
				c.Next()
				return
			}

			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "Authorization header format must be Bearer {token}"})
			return
		}

		token := strings.TrimPrefix(authHeader, "Bearer ")

		claims, err := jwtService.Verify(token)
		if err != nil {
			if authMode == config.AuthOptional {
				stripIdentityHeaders(c)
				c.Next()
				return
			}

			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"message": "Invalid access token"})
			return
		}

		// Make claims available to downstream Gin handlers/middleware
		// (e.g. Authorize) via the request context.
		c.Set(constants.ContextClaims, claims)

		// Strip any client-supplied identity headers first, so a
		// caller can't spoof another user by setting these directly.
		stripIdentityHeaders(c)

		// Re-inject identity headers derived from the verified token,
		// so downstream proxied services can trust them.
		c.Request.Header.Set(constants.HeaderUserID, claims.Subject)
		c.Request.Header.Set(constants.HeaderUserEmail, claims.Email)
		c.Request.Header.Set(constants.HeaderUserRoles, strings.Join(claims.Roles, ","))

		c.Next()
	}
}


func stripIdentityHeaders(c *gin.Context) {
	c.Request.Header.Del(constants.HeaderUserID)
	c.Request.Header.Del(constants.HeaderUserEmail)
	c.Request.Header.Del(constants.HeaderUserRoles)
}