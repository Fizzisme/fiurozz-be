package com.philia.projectservice.catalog.internal.application.exception;

public final class ProjectNotEditableException extends RuntimeException {

    public ProjectNotEditableException() {
        super("Archived projects must be reopened before they can be edited.");
    }
}
