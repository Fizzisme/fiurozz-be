package com.philia.projectservice.catalog.api;

import com.philia.projectservice.catalog.internal.domain.exception.InvalidProjectException;
import java.util.UUID;

public record PublicProjectSearchQuery(String search, UUID categoryId, UUID subCategoryId, String tag,
                                       UUID ownerId, int page, int size, SortOrder sort) {
    public PublicProjectSearchQuery {
        if (page < 0) throw new InvalidProjectException("Page must be zero or greater.");
        if (size < 1 || size > 50) throw new InvalidProjectException("Size must be between 1 and 50.");
        search = blankToNull(search);
        tag = blankToNull(tag);
        sort = sort == null ? SortOrder.NEWEST : sort;
    }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    public enum SortOrder { NEWEST, OLDEST }
}
