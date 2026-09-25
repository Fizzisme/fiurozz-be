package com.philia.projectservice.catalog.internal.adapter.in.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateProjectRequest(
        @NotNull(message = "subCategoryId is required")
        UUID subCategoryId,

        @NotBlank(message = "title is required")
        @Size(max = 180, message = "title must not exceed 180 characters")
        String title,

        @NotBlank(message = "shortDescription is required")
        @Size(max = 500, message = "shortDescription must not exceed 500 characters")
        String shortDescription,

        @NotBlank(message = "description is required")
        @Size(max = 50_000, message = "description must not exceed 50000 characters")
        String description,

        @Size(max = 500, message = "demoUrl must not exceed 500 characters")
        @Pattern(regexp = "^https?://[^\\s]+$", message = "demoUrl must be a valid HTTP or HTTPS URL")
        String demoUrl,

        @Size(max = 500, message = "githubUrl must not exceed 500 characters")
        @Pattern(regexp = "^https://github\\.com/[^\\s]+$", message = "githubUrl must be a valid GitHub HTTPS URL")
        String githubUrl,

        @Pattern(
                regexp = "^(PUBLIC|UNLISTED|PRIVATE)$",
                flags = Pattern.Flag.CASE_INSENSITIVE,
                message = "visibility must be PUBLIC, UNLISTED, or PRIVATE"
        )
        String visibility,

        @Size(max = 20, message = "techStack may contain at most 20 items")
        List<@NotBlank(message = "techStack items must not be blank")
                @Size(max = 60, message = "techStack items must not exceed 60 characters") String> techStack,

        @Size(max = 30, message = "features may contain at most 30 items")
        List<@NotBlank(message = "feature items must not be blank")
                @Size(max = 200, message = "feature items must not exceed 200 characters") String> features,

        @Size(max = 10, message = "tagIds may contain at most 10 items")
        List<@NotNull(message = "tagIds must not contain null values") UUID> tagIds
) {
}
