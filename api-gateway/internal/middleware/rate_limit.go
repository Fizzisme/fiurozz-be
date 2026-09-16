package middleware

import (
	"net/http"
	"github.com/gin-gonic/gin"
	"golang.org/x/time/rate"
	"github.com/fizzisme/api-gateway/internal/ratelimit"
	"github.com/fizzisme/api-gateway/internal/helper"
)

// RateLimit returns a middleware that enforces a token-bucket rate
// limit per key (see helper.BuildRateLimitKey), using shared limiter
// state from manager. routeName scopes the key so each route has its
// own bucket per user/IP. Requests exceeding the limit are rejected
// with 429 Too Many Requests.
func RateLimit(
	manager *ratelimit.Manager,
	routeName string,
	limit rate.Limit,
	burst int,
) gin.HandlerFunc {

	return func(c *gin.Context) {
		key := helper.BuildRateLimitKey(c, routeName)

		// Fetches (or lazily creates) the limiter for this key,
		// so each user/IP gets its own independent bucket.
		limiter := manager.GetLimiter(
			key,
			limit,
			burst,
		)

		if !limiter.Allow() {
			c.AbortWithStatusJSON(
				http.StatusTooManyRequests,
				gin.H{
					"error": "rate limit exceeded",
				},
			)
			return
		}

		c.Next()

	}
}