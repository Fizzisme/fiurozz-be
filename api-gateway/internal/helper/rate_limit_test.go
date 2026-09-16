package helper

import (
	"net/http/httptest"
	"testing"

	"github.com/fizzisme/api-gateway/internal/auth"
	"github.com/fizzisme/api-gateway/internal/constants"
	"github.com/gin-gonic/gin"
	"github.com/golang-jwt/jwt/v5"
)

func TestBuildRateLimitKey(t *testing.T) {

	gin.SetMode(gin.TestMode)

	tests := []struct {
		name      string
		routeName string
		setup     func(c *gin.Context)
		want      string
	}{
		{
			name:      "authenticated user is keyed by route and subject",
			routeName: "project",
			setup: func(c *gin.Context) {
				c.Set(constants.ContextClaims, &auth.Claims{
					RegisteredClaims: jwt.RegisteredClaims{
						Subject: "u1",
					},
				})
			},
			want: "project:user:u1",
		},
		{
			name:      "same user gets a different key on a different route",
			routeName: "user",
			setup: func(c *gin.Context) {
				c.Set(constants.ContextClaims, &auth.Claims{
					RegisteredClaims: jwt.RegisteredClaims{
						Subject: "u1",
					},
				})
			},
			want: "user:user:u1",
		},
		{
			name:      "no claims falls back to client IP",
			routeName: "auth",
			setup:     func(c *gin.Context) {},
			want:      "auth:ip:203.0.113.5",
		},
		{
			name:      "wrong type stored under claims key falls back to IP",
			routeName: "auth",
			setup: func(c *gin.Context) {
				c.Set(constants.ContextClaims, "not-a-claims-pointer")
			},
			want: "auth:ip:203.0.113.5",
		},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {

			w := httptest.NewRecorder()
			c, _ := gin.CreateTestContext(w)

			c.Request = httptest.NewRequest("GET", "/", nil)
			c.Request.RemoteAddr = "203.0.113.5:54321"

			tc.setup(c)

			got := BuildRateLimitKey(c, tc.routeName)

			if got != tc.want {
				t.Errorf("BuildRateLimitKey() = %q, want %q", got, tc.want)
			}
		})
	}
}
