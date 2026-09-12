package com.philia.projectservice.catalog.internal.adapter.in.web.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProjectDetailResponse(
        UUID id,
        OwnerResponse owner,
        CategoryResponse category,
        SubCategoryResponse subCategory,
        String title,
        String slug,
        String shortDescription,
        String description,
        String thumbnailUrl,
        List<String> images,
        String demoUrl,
        String githubUrl,
        List<String> techStack,
        List<String> features,
        List<TagResponse> tags,
        String status,
        String visibility,
        String sourceVisibility,
        StatisticsResponse statistics,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt,
        long version
) {

    public record OwnerResponse(UUID id, String displayName, String avatarUrl) {
    }

    public record CategoryResponse(UUID id, String key, String slug, String title, String icon) {
    }

    public record SubCategoryResponse(UUID id, String key, String slug, String title) {
    }

    public record TagResponse(UUID id, String slug, String displayName) {
    }

    public record StatisticsResponse(long viewCount, long likeCount, long commentCount) {
    }
}
