package middleware

import (
	"time"
	"github.com/gin-gonic/gin"
	"github.com/fizzisme/api-gateway/internal/constants"
	"github.com/fizzisme/api-gateway/internal/logger"
	"go.uber.org/zap"
	"github.com/fizzisme/api-gateway/internal/auth"
)

// Logger returns a middleware that logs one structured entry per
// request, including request ID, method, path, status code, latency,
// and client IP. Must run after any middleware that sets
// constants.ContextRequestID for the ID to appear in logs.
func Logger() gin.HandlerFunc {

	return func(c *gin.Context) {

		start := time.Now()

		// May be nil if no upstream middleware has set a request ID yet.
		requestID, _ := c.Get(constants.ContextRequestID)

		deviceName, _ := c.Get(constants.ContextDeviceName)

		c.Next()

		userID := ""

		if value, ok := c.Get(constants.ContextClaims); ok {

			if claims, ok := value.(*auth.Claims); ok {
				userID = claims.Subject
			}
		}

		// Let the request run through the rest of the chain first,
		// so status code and duration reflect the final outcome.
		fields := []zap.Field{
			zap.Any("request_id", requestID),
			zap.String("user_id", userID),
			zap.String("method", c.Request.Method),
			zap.String("path", c.Request.URL.Path),
			zap.String("query", c.Request.URL.RawQuery),
			zap.Int("status", c.Writer.Status()),
			zap.Int("response_bytes", c.Writer.Size()),
			zap.Duration("duration", time.Since(start)),
			zap.String("client_ip", c.ClientIP()),
			zap.Any("device_name", deviceName),
			zap.String("user_agent", c.Request.UserAgent()),
		}

		status := c.Writer.Status()

		switch{
		case status >= 500:
			logger.Log.Error("HTTP Request", fields...)
		
		case status >= 400:
			logger.Log.Warn("HTTP Request", fields...)
		default:
			logger.Log.Info("HTTP Request", fields...)
		}
	}
}