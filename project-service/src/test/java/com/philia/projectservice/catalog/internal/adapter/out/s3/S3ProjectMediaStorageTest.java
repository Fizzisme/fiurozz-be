package com.philia.projectservice.catalog.internal.adapter.out.s3;

import com.philia.projectservice.catalog.internal.application.exception.MediaStorageUnavailableException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Error;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class S3ProjectMediaStorageTest {

    private final S3Client client = mock(S3Client.class);

    @Test
    void putStoresObjectWithContentMetadata() {
        var storage = storage(false);

        storage.put("projects/p1/a.png", "image/png", 3, new ByteArrayInputStream(new byte[]{1, 2, 3}));

        var request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo("project-media");
        assertThat(request.getValue().key()).isEqualTo("projects/p1/a.png");
        assertThat(request.getValue().contentType()).isEqualTo("image/png");
        assertThat(request.getValue().contentLength()).isEqualTo(3);
        verify(client, never()).headBucket(any(HeadBucketRequest.class));
    }

    @Test
    void putCreatesMissingBucketWithPublicPolicyOnlyOnce() {
        var storage = storage(true);
        when(client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).build());

        storage.put("projects/p1/a.png", "image/png", 1, new ByteArrayInputStream(new byte[]{1}));
        storage.put("projects/p1/b.png", "image/png", 1, new ByteArrayInputStream(new byte[]{1}));

        verify(client, times(1)).createBucket(any(CreateBucketRequest.class));
        var policy = ArgumentCaptor.forClass(PutBucketPolicyRequest.class);
        verify(client, times(1)).putBucketPolicy(policy.capture());
        assertThat(policy.getValue().policy()).contains("arn:aws:s3:::project-media/projects/*");
        verify(client, times(2)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void putWrapsStorageFailures() {
        var storage = storage(false);
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.create("connection refused"));

        assertThatThrownBy(() -> storage.put(
                "projects/p1/a.png", "image/png", 1, new ByteArrayInputStream(new byte[]{1})))
                .isInstanceOf(MediaStorageUnavailableException.class)
                .hasCauseInstanceOf(SdkClientException.class);
    }

    @Test
    void deleteAllIgnoresEmptyInput() {
        storage(false).deleteAll(List.of());

        verifyNoInteractions(client);
    }

    @Test
    void deleteAllFailsWhenStorageReportsErrors() {
        var storage = storage(false);
        when(client.deleteObjects(any(DeleteObjectsRequest.class))).thenReturn(DeleteObjectsResponse.builder()
                .errors(S3Error.builder().key("projects/p1/a.png").code("AccessDenied").build())
                .build());

        assertThatThrownBy(() -> storage.deleteAll(List.of("projects/p1/a.png")))
                .isInstanceOf(MediaStorageUnavailableException.class);
    }

    @Test
    void publicUrlEncodesEachPathSegment() {
        var url = storage(false).publicUrl("projects/p1/my file.png");

        assertThat(url).isEqualTo("http://localhost:9000/project-media/projects/p1/my%20file.png");
    }

    private S3ProjectMediaStorage storage(boolean autoCreateBucket) {
        return new S3ProjectMediaStorage(client, new S3StorageProperties(
                URI.create("http://minio:9000"),
                "us-east-1",
                "key",
                "secret",
                "project-media",
                "http://localhost:9000/",
                autoCreateBucket
        ));
    }
}
