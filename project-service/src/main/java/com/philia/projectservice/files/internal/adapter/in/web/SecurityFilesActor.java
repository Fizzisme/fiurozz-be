package com.philia.projectservice.files.internal.adapter.in.web;

import com.philia.projectservice.files.internal.domain.exception.FilesException;
import com.philia.projectservice.files.internal.application.port.out.FilesActor;
import com.philia.projectservice.shared.security.GatewayActorPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class SecurityFilesActor implements FilesActor {
    @Override
    public UUID requiredUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof GatewayActorPrincipal principal)) {
            throw new FilesException(401, "AUTHENTICATION_REQUIRED", "Authentication is required.");
        }
        return principal.id();
    }
}
