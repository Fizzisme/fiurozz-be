package com.philia.projectservice.catalog.internal.application.port.out;

import com.philia.projectservice.catalog.api.ProjectDetailResult;

import java.util.Optional;
import java.util.UUID;

/**
 * Output port for loading an active project detail projection from persistence.
 * Caller-specific authorization remains in the application layer.
 */
public interface ProjectDetailQuery {

    /**
     * Finds a non-deleted project. An empty result also represents a soft-deleted project.
     */
    Optional<ProjectDetailResult> findActiveById(UUID projectId);

    default Optional<ProjectDetailResult> findPublicByOwnerAndSlug(UUID ownerId, String slug) {
        return Optional.empty();
    }
}
