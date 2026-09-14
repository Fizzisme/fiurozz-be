package com.philia.projectservice.catalog.api;

import java.util.List;
import java.util.UUID;

/** The mutable project fields supplied by PATCH /v1/projects/{projectId}. */
public record UpdateProjectCommand(
        UUID projectId,
        long expectedVersion,
        UUID subCategoryId,
        String title,
        String slug,
        String shortDescription,
        String description,
        String demoUrl,
        List<String> techStack,
        List<String> features
) {
    public boolean hasChanges() {
        return subCategoryId != null || title != null || slug != null || shortDescription != null
                || description != null || demoUrl != null || techStack != null || features != null;
    }
}
