package com.philia.projectservice.catalog.api;

import java.util.UUID;

/**
 * Publishes one project using the version supplied through its HTTP ETag.
 */
public record PublishProjectCommand(UUID projectId, long expectedVersion) {
}
