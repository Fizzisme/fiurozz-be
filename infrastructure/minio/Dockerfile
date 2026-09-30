FROM golang:1.25-alpine AS build

ARG MINIO_COMMIT=7aac2a2c5b7c882e68c1ce017d8256be2feea27f

RUN apk add --no-cache git
RUN CGO_ENABLED=0 go install github.com/minio/minio@${MINIO_COMMIT}

FROM alpine:3.22

RUN apk add --no-cache ca-certificates curl \
    && addgroup -S minio \
    && adduser -S minio -G minio \
    && mkdir -p /data \
    && chown minio:minio /data

COPY --from=build /go/bin/minio /usr/local/bin/minio

USER minio

EXPOSE 9000 9001

ENTRYPOINT ["minio"]
CMD ["server", "/data", "--console-address", ":9001"]
