package com.philia.projectservice.files.internal.adapter.out.s3;

import com.philia.projectservice.files.internal.domain.exception.FilesException;
import com.philia.projectservice.files.internal.application.port.out.FileBlobStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import java.util.ArrayList;
import java.util.List;

/** Content-addressed text blobs. No public policy or object URL is ever created. */
@Component
public class S3FileBlobStorage implements FileBlobStorage {
    private static final Logger log = LoggerFactory.getLogger(S3FileBlobStorage.class);
    private static final String PREFIX = "blobs/sha256/";
    private final S3Client client;
    private final FileStorageProperties properties;
    private volatile boolean ready;

    public S3FileBlobStorage(@Qualifier("projectFilesS3Client") S3Client client, FileStorageProperties properties) {
        this.client = client; this.properties = properties;
    }

    @Override
    public void putIfAbsent(String hash, byte[] content) {
        try {
            ensureBucket();
            try {
                client.headObject(HeadObjectRequest.builder().bucket(properties.bucket()).key(key(hash)).build());
                return;
            } catch (S3Exception exception) {
                if (exception.statusCode() != 404) throw exception;
            }
            try {
                client.putObject(PutObjectRequest.builder().bucket(properties.bucket()).key(key(hash))
                        .contentType("text/plain; charset=utf-8").ifNoneMatch("*").build(), RequestBody.fromBytes(content));
            } catch (S3Exception exception) {
                // Another identical upload may have completed; never overwrite immutable content.
                if (exception.statusCode() != 412) throw exception;
            }
        } catch (RuntimeException exception) { throw unavailable(exception); }
    }

    @Override
    public byte[] read(String hash) {
        try {
            return client.getObjectAsBytes(GetObjectRequest.builder().bucket(properties.bucket()).key(key(hash)).build()).asByteArray();
        } catch (RuntimeException exception) { throw unavailable(exception); }
    }

    @Override
    public List<Blob> list() {
        try {
            ensureBucket();
            var result = new ArrayList<Blob>();
            var request = ListObjectsV2Request.builder().bucket(properties.bucket()).prefix(PREFIX).build();
            for (var page : client.listObjectsV2Paginator(request)) {
                for (var object : page.contents()) {
                    var hash = object.key().substring(PREFIX.length());
                    if (hash.matches("[0-9a-f]{64}")) result.add(new Blob(hash, object.lastModified()));
                }
            }
            return result;
        } catch (RuntimeException exception) { throw unavailable(exception); }
    }

    @Override
    public void delete(String hash) {
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket()).key(key(hash)).build());
        } catch (RuntimeException exception) { throw unavailable(exception); }
    }

    private synchronized void ensureBucket() {
        if (ready) return;
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(properties.bucket()).build());
        } catch (S3Exception exception) {
            if (exception.statusCode() != 404 || !properties.autoCreateBucket()) throw exception;
            try {
                client.createBucket(CreateBucketRequest.builder().bucket(properties.bucket()).build());
            } catch (S3Exception race) {
                if (!"BucketAlreadyOwnedByYou".equals(race.awsErrorDetails().errorCode())) throw race;
            }
        }
        ready = true;
    }

    private static String key(String hash) {
        if (hash == null || !hash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid SHA-256 blob key.");
        return PREFIX + hash;
    }

    private static FilesException unavailable(RuntimeException exception) {
        log.error("Private project file storage failed", exception);
        return new FilesException(503, "FILES_STORAGE_UNAVAILABLE", "File storage is temporarily unavailable.");
    }
}
