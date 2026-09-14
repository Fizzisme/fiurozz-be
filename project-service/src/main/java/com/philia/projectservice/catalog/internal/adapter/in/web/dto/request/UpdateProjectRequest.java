package com.philia.projectservice.catalog.internal.adapter.in.web.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Optional owner-editable fields for PATCH /v1/projects/{projectId}. */
public record UpdateProjectRequest(
        UUID subCategoryId,

        @Size(max = 180, message = "title must not exceed 180 characters")
        String title,

        @Size(max = 180, message = "slug must not exceed 180 characters")
        String slug,

        @Size(max = 500, message = "shortDescription must not exceed 500 characters")
        String shortDescription,

        @Size(max = 50_000, message = "description must not exceed 50000 characters")
        String description,

        @Size(max = 500, message = "demoUrl must not exceed 500 characters")
        @Pattern(regexp = "^https://[^\\s]+$", message = "demoUrl must be a valid HTTPS URL")
        String demoUrl,

        @Size(max = 20, message = "techStack may contain at most 20 items")
        List<@Size(max = 60, message = "techStack items must not exceed 60 characters") String> techStack,

        @Size(max = 30, message = "features may contain at most 30 items")
        List<@Size(max = 200, message = "feature items must not exceed 200 characters") String> features
) {
}
