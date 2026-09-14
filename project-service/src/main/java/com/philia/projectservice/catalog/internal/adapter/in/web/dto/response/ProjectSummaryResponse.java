package com.philia.projectservice.catalog.internal.adapter.in.web.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Compact project representation used in collection responses.")
public record ProjectSummaryResponse(
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
