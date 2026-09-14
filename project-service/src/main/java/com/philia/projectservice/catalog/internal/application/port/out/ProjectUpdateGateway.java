package com.philia.projectservice.catalog.internal.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence operations required to update the owner-editable project fields. */
public interface ProjectUpdateGateway {

    Optional<ProjectState> findActiveState(UUID projectId);

    boolean slugExistsForAnotherActiveProject(UUID ownerId, String slug, UUID projectId);

    boolean updateIfCurrent(UpdateData update);

    record ProjectState(
            UUID ownerId,
            String status,
            long version,
            UUID subCategoryId,
            String title,
            String slug,
            String shortDescription,
            String description,
            String demoUrl,
            List<String> techStack,
            List<String> features
    ) {
    }

    record UpdateData(
            UUID projectId,
            UUID ownerId,
            long expectedVersion,
            UUID subCategoryId,
            String title,
            String slug,
            String shortDescription,
            String description,
            String demoUrl,
            List<String> techStack,
            List<String> features,
            Instant updatedAt
    ) {
    }
}
