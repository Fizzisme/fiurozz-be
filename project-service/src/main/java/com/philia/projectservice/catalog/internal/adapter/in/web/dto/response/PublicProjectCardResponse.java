package com.philia.projectservice.catalog.internal.adapter.in.web.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Project data required to render a public discovery card.")
public record PublicProjectCardResponse(
        UUID id,
        String title,
        String slug,
        String shortDescription,
        String thumbnailUrl,
        CategoryResponse category,
        SubCategoryResponse subCategory,
        List<String> techStack,
        OwnerResponse owner,
        Instant publishedAt,
        Instant createdAt
) {
    public record CategoryResponse(UUID id, String slug, String title) {
    }

    public record SubCategoryResponse(UUID id, String slug, String title) {
    }

    public record OwnerResponse(UUID id, String displayName, String avatarUrl) {
    }
}
