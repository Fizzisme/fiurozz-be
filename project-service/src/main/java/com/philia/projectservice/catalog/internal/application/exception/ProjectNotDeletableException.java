package com.philia.projectservice.catalog.internal.application.exception;

public final class ProjectNotDeletableException extends RuntimeException {

    public ProjectNotDeletableException() {
        super("Published projects must be archived before they can be deleted.");
    }
}
