package com.philia.projectservice.files.internal.adapter.out.s3;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.net.URI;

/** Separate PRIVATE bucket; never point this at the public project-media bucket. */
@ConfigurationProperties(prefix = "storage.files")
public record FileStorageProperties(URI endpoint, String region, String accessKey, String secretKey,
                                    String bucket, boolean autoCreateBucket, boolean gcEnabled,
                                    long gcGraceHours) {}
