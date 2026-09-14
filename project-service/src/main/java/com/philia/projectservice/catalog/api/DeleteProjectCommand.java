package com.philia.projectservice.catalog.api;

import java.util.UUID;

/** Identifies the project and ETag required to soft-delete it. */
public record DeleteProjectCommand(UUID projectId, long expectedVersion) {
}
