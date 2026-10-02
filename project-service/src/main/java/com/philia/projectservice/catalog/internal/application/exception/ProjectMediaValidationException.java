package com.philia.projectservice.catalog.internal.application.exception;

/** A media file is missing, of an unsupported format, too large, or present in an invalid quantity. */
public class ProjectMediaValidationException extends RuntimeException {

    private final String field;

    public ProjectMediaValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
