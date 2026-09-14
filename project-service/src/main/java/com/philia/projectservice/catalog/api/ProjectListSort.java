package com.philia.projectservice.catalog.api;

import com.philia.projectservice.catalog.internal.domain.exception.InvalidProjectException;

import java.util.Arrays;

/**
 * The only sort orders exposed by the owner-project listing API.
 */
public enum ProjectListSort {
    CREATED_AT_DESC("createdAt,desc", "DESC", "createdAt"),
    CREATED_AT_ASC("createdAt,asc", "ASC", "createdAt"),
    UPDATED_AT_DESC("updatedAt,desc", "DESC", "updatedAt");

    private final String requestValue;
    private final String direction;
    private final String property;

    ProjectListSort(String requestValue, String direction, String property) {
        this.requestValue = requestValue;
        this.direction = direction;
        this.property = property;
    }

    public static ProjectListSort fromRequest(String value) {
        var requestedSort = value == null || value.isBlank() ? CREATED_AT_DESC.requestValue : value.trim();
        return Arrays.stream(values())
                .filter(sort -> sort.requestValue.equals(requestedSort))
                .findFirst()
                .orElseThrow(() -> new InvalidProjectException(
                        "Unsupported project list sort: " + requestedSort
                ));
    }

    public String direction() {
        return direction;
    }

    public String property() {
        return property;
    }
}
