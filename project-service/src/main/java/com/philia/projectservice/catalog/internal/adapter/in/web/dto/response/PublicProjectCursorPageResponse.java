package com.philia.projectservice.catalog.internal.adapter.in.web.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Cursor page for the public project discovery feed.")
public record PublicProjectCursorPageResponse(
        List<PublicProjectCardResponse> items,
        String nextCursor,
        boolean hasMore
) {
    public PublicProjectCursorPageResponse {
        items = List.copyOf(items);
    }
}
