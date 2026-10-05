package com.philia.projectservice.files.internal.application.port.out;

import java.util.UUID;

/** The user identity verified by the gateway, never supplied by a request body. */
public interface FilesActor {
    UUID requiredUserId();
}
