package com.philia.projectservice.catalog.internal.adapter.out.s3;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

/**
 * S3-compatible (MinIO locally) storage settings for project media.
 *
 * @param endpoint      endpoint used by the service itself, for example {@code http://minio:9000} inside Docker
 * @param publicBaseUrl base URL used to build public object URLs stored in {@code project_media.media_url};
 *                      it must be reachable from the browser
 */
@ConfigurationProperties(prefix = "storage.s3")
public record S3StorageProperties(
        URI endpoint,
        String region,
        String accessKey,
        String secretKey,
        String bucket,
        String publicBaseUrl,
        boolean autoCreateBucket
) {
}
