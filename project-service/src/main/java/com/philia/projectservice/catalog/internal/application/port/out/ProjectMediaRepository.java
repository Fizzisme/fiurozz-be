package com.philia.projectservice.catalog.internal.application.port.out;

import com.philia.projectservice.catalog.internal.domain.ProjectMediaType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ProjectMediaRepository {

    void addAll(UUID projectId, List<NewProjectMedia> media, Instant createdAt);

    /** A media object already uploaded to storage, ready to be recorded in {@code project_media}. */
    record NewProjectMedia(
            UUID id,
            ProjectMediaType type,
            String url,
            String objectKey,
            String contentType,
            long sizeBytes,
            int sortOrder
    ) {
    }
}
