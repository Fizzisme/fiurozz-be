package com.philia.projectservice.catalog.api;

import java.util.List;

/**
 * Framework-independent page result used by project collection APIs.
 */
public record ProjectPageResult<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public ProjectPageResult {
        items = List.copyOf(items);
    }
}
