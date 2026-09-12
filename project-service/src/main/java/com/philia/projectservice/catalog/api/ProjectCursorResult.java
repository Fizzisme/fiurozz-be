package com.philia.projectservice.catalog.api;

import java.util.List;

/**
 * Framework-independent cursor page used by the public discovery feed.
 */
public record ProjectCursorResult<T>(
        List<T> items,
        PublicProjectCursor nextCursor,
        boolean hasMore
) {
    public ProjectCursorResult {
        items = List.copyOf(items);
    }
}
