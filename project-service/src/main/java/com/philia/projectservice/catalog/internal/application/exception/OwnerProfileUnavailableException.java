package com.philia.projectservice.catalog.internal.application.exception;

import java.util.UUID;

public final class OwnerProfileUnavailableException extends RuntimeException {

    public OwnerProfileUnavailableException(UUID ownerId) {
        super("The profile of owner " + ownerId + " has not been synchronised yet. Please try again shortly.");
    }
}
