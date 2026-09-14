package com.philia.projectservice.catalog.internal.application.port.out;

import com.philia.projectservice.catalog.internal.domain.ProjectVisibility;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Atomic project lifecycle and visibility persistence operations. */
public interface ProjectLifecycleGateway {

    Optional<ProjectState> findActiveState(UUID projectId);

    boolean archiveIfCurrent(UUID projectId, UUID ownerId, long expectedVersion, Instant updatedAt);

    boolean reopenIfCurrent(UUID projectId, UUID ownerId, long expectedVersion, Instant updatedAt);

    boolean changeVisibilityIfCurrent(
            UUID projectId,
            UUID ownerId,
            long expectedVersion,
            ProjectVisibility visibility,
            Instant updatedAt
    );

    record ProjectState(UUID ownerId, String status, long version) {
    }
}
