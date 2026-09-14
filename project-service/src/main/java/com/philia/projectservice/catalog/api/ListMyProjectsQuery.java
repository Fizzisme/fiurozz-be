package com.philia.projectservice.catalog.api;

import com.philia.projectservice.catalog.internal.domain.ProjectStatus;
import com.philia.projectservice.catalog.internal.domain.ProjectVisibility;
import com.philia.projectservice.catalog.internal.domain.exception.InvalidProjectException;

/**
 * Validated filters and pagination for an owner's project list.
 */
public record ListMyProjectsQuery(
        ProjectStatus status,
        ProjectVisibility visibility,
        String search,
        int page,
        int size,
        ProjectListSort sort
) {

    public ListMyProjectsQuery {
        if (page < 0) {
            throw new InvalidProjectException("Page must be zero or greater.");
        }
        if (size < 1 || size > 50) {
            throw new InvalidProjectException("Size must be between 1 and 50.");
        }
        if (sort == null) {
            throw new InvalidProjectException("Sort is required.");
        }
        search = search == null || search.isBlank() ? null : search.trim();
    }
}
