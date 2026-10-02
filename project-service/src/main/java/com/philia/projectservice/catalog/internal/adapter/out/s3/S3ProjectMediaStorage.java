package com.philia.projectservice.catalog.internal.adapter.out.s3;

import com.philia.projectservice.catalog.internal.application.exception.MediaStorageUnavailableException;
import com.philia.projectservice.catalog.internal.application.port.out.ProjectMediaStorage;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

@Component
public class S3ProjectMediaStorage implements ProjectMediaStorage {

    static final String KEY_PREFIX = "projects/";

    private final S3Client client;
    private final String bucket;
    private final String publicBaseUrl;
    private final boolean autoCreateBucket;
    private volatile boolean bucketReady;

    public S3ProjectMediaStorage(S3Client client, S3StorageProperties properties) {
        this.client = client;
        this.bucket = properties.bucket();
        this.publicBaseUrl = properties.publicBaseUrl().replaceAll("/+$", "");
        this.autoCreateBucket = properties.autoCreateBucket();
    }

    @Override
    public void put(String objectKey, String contentType, long sizeBytes, InputStream content) {
        try {
            ensureBucket();
            client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(objectKey)
                            .contentType(contentType)
                            .contentLength(sizeBytes)
                            .contentDisposition("inline")
                            // Keys are random UUIDs and never overwritten, so clients may cache forever.
                            .cacheControl("public, max-age=31536000, immutable")
                            .build(),
                    RequestBody.fromInputStream(content, sizeBytes)
            );
        } catch (SdkException exception) {
            throw new MediaStorageUnavailableException("Failed to store project media " + objectKey, exception);
        }
    }

    @Override
    public void deleteAll(Collection<String> objectKeys) {
        if (objectKeys == null || objectKeys.isEmpty()) {
            return;
        }
        var identifiers = objectKeys.stream()
                .map(key -> ObjectIdentifier.builder().key(key).build())
                .toList();
        try {
            var response = client.deleteObjects(DeleteObjectsRequest.builder()
                    .bucket(bucket)
                    .delete(Delete.builder().objects(identifiers).quiet(true).build())
                    .build());
            if (response.hasErrors() && !response.errors().isEmpty()) {
                throw new MediaStorageUnavailableException(
                        "Failed to delete " + response.errors().size() + " project media objects", null);
            }
        } catch (SdkException exception) {
            throw new MediaStorageUnavailableException("Failed to delete project media objects", exception);
        }
    }

    @Override
    public String publicUrl(String objectKey) {
        var encodedKey = Arrays.stream(objectKey.split("/"))
                .map(S3ProjectMediaStorage::encodePathSegment)
                .collect(Collectors.joining("/"));
        return publicBaseUrl + "/" + encodePathSegment(bucket) + "/" + encodedKey;
    }

    private void ensureBucket() {
        if (!autoCreateBucket || bucketReady) {
            return;
        }
        synchronized (this) {
            if (bucketReady) {
                return;
            }
            createBucketIfMissing();
            bucketReady = true;
        }
    }

    private void createBucketIfMissing() {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException exception) {
            createBucket();
        } catch (S3Exception exception) {
            // HeadBucket has no response body, so a missing bucket may surface as a bare 404.
            if (exception.statusCode() != 404) {
                throw exception;
            }
            createBucket();
        }

        // Stored media URLs are public, so only the projects/ prefix is readable anonymously.
        client.putBucketPolicy(PutBucketPolicyRequest.builder().bucket(bucket).policy("""
                {
                  "Version": "2012-10-17",
                  "Statement": [
                    {
                      "Sid": "PublicProjectMediaRead",
                      "Effect": "Allow",
                      "Principal": "*",
                      "Action": ["s3:GetObject"],
                      "Resource": ["arn:aws:s3:::%s/%s*"]
                    }
                  ]
                }
                """.formatted(bucket, KEY_PREFIX)).build());
    }

    private void createBucket() {
        try {
            client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        } catch (BucketAlreadyOwnedByYouException | BucketAlreadyExistsException ignored) {
            // Another instance created it first.
        }
    }

    private static String encodePathSegment(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
