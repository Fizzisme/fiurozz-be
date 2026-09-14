package com.philia.projectservice.catalog.internal.adapter.in.web.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** JSON body for PUT /v1/projects/{projectId}/tags. */
public record ReplaceProjectTagsRequest(
        @NotNull(message = "tagIds is required")
        @Size(max = 10, message = "A project may have at most 10 tags")
        List<@NotNull(message = "tagIds must not contain null values") UUID> tagIds
) {
}
