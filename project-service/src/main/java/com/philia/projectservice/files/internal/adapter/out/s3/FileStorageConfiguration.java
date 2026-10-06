package com.philia.projectservice.files.internal.adapter.out.s3;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FileStorageProperties.class)
public class FileStorageConfiguration {
    @Bean(destroyMethod = "close")
    S3Client projectFilesS3Client(FileStorageProperties properties) {
        return S3Client.builder().endpointOverride(properties.endpoint()).forcePathStyle(true)
                .region(Region.of(properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofSeconds(15)).apiCallAttemptTimeout(Duration.ofSeconds(5)).build())
                .build();
    }
}
