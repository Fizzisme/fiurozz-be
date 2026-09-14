package com.philia.projectservice.catalog.internal.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Persistence operations required to validate and publish a draft project. */
public interface ProjectPublicationGateway {

    Optional<ProjectState> findActiveState(UUID projectId);

    boolean publishIfCurrent(UUID projectId, UUID ownerId, long expectedVersion, Instant publishedAt);

    record ProjectState(
            UUID ownerId,
            String status,
            long version,
            UUID subCategoryId,
            Set<UUID> tagIds,
            String title,
            String slug,
            String shortDescription,
            String description,
            String demoUrl
    ) {
    }
}
