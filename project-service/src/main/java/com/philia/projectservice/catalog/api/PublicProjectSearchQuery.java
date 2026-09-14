package com.philia.projectservice.catalog.api;

import com.philia.projectservice.catalog.internal.domain.exception.InvalidProjectException;

import java.util.Locale;
import java.util.UUID;

public record PublicProjectSearchQuery(
        String search,
        UUID categoryId,
        UUID subCategoryId,
        String categorySlug,
        String subCategorySlug,
        String tag,
        UUID ownerId,
        PublicProjectCursor cursor,
        int limit,
        SortOrder sort
) {
    public PublicProjectSearchQuery {
        if (limit < 1 || limit > 50) {
            throw new InvalidProjectException("Limit must be between 1 and 50.");
        }
        search = blankToNull(search);
        categorySlug = normalizedSlug(categorySlug);
        subCategorySlug = normalizedSlug(subCategorySlug);
        tag = normalizedSlug(tag);
        sort = sort == null ? SortOrder.NEWEST : sort;
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private static String normalizedSlug(String value) {
        var normalized = blankToNull(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    public enum SortOrder { NEWEST, OLDEST }
}
