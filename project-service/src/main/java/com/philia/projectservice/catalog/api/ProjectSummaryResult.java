package com.philia.projectservice.catalog.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Compact representation used in project collection responses.
 */
public record ProjectSummaryResult(
        UUID id,
        String title,
        String slug,
        String shortDescription,
        String thumbnailUrl,
        String status,
        String visibility,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
}
