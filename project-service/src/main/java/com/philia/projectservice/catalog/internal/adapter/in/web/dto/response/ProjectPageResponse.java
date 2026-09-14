package com.philia.projectservice.catalog.internal.adapter.in.web.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "A zero-based page of projects.")
public record ProjectPageResponse(
        List<ProjectSummaryResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
