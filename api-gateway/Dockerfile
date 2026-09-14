FROM golang:1.26-alpine AS build

WORKDIR /workspace

COPY go.mod go.sum ./
RUN go mod download

COPY . .
RUN CGO_ENABLED=0 go build -o /out/api-gateway ./cmd/server

FROM alpine:3.20

RUN apk add --no-cache ca-certificates

WORKDIR /app

RUN addgroup -S gateway && adduser -S gateway -G gateway

COPY --from=build /out/api-gateway ./api-gateway
COPY configs/routes.yaml ./configs/routes.yaml

USER gateway

EXPOSE 8080

ENTRYPOINT ["./api-gateway"]
