package com.philia.projectservice.catalog.internal.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Persistence operations required to soft-delete a project. */
public interface ProjectDeleteGateway {

    Optional<ProjectState> findActiveState(UUID projectId);

    boolean softDeleteIfCurrent(UUID projectId, UUID ownerId, long expectedVersion, Instant deletedAt);

    record ProjectState(UUID ownerId, String status, long version) {
    }
}
