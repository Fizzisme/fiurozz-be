package com.philia.projectservice.catalog.api;

import com.philia.projectservice.catalog.internal.domain.ProjectVisibility;

import java.util.UUID;

/**
 * Publishes one project using the version supplied through its HTTP ETag and
 * applies the chosen visibility in the same transition.
 */
public record PublishProjectCommand(UUID projectId, long expectedVersion, ProjectVisibility visibility) {
}
