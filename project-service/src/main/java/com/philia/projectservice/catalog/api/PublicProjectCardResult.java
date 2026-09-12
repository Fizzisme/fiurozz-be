package com.philia.projectservice.catalog.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public, compact projection containing exactly what a discovery card needs.
 */
public record PublicProjectCardResult(
        UUID id,
        String title,
        String slug,
        String shortDescription,
        String thumbnailUrl,
        Category category,
        SubCategory subCategory,
        List<String> techStack,
        Owner owner,
        Instant publishedAt,
        Instant createdAt
) {
    public PublicProjectCardResult {
        techStack = List.copyOf(techStack);
    }

    public record Category(UUID id, String slug, String title) {
    }

    public record SubCategory(UUID id, String slug, String title) {
    }

    public record Owner(UUID id, String displayName, String avatarUrl) {
    }
}
