package middleware

import (
	"github.com/gin-gonic/gin"
	"github.com/fizzisme/api-gateway/internal/constants"
	"github.com/mileusna/useragent"
)

func ClientInfo() gin.HandlerFunc {
    return func(c *gin.Context) {

        ua := useragent.Parse(c.Request.UserAgent())

		device := ua.Name
		if ua.OS != "" {
			device += " on " + ua.OS
		}

        c.Request.Header.Set(
			constants.HeaderDeviceName,
			device,
		)

		c.Set(constants.ContextDeviceName, device)

        c.Next()
    }
}