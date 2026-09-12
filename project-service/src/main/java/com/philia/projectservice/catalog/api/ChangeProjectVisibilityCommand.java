package com.philia.projectservice.catalog.api;

import com.philia.projectservice.catalog.internal.domain.ProjectVisibility;

import java.util.UUID;

public record ChangeProjectVisibilityCommand(
        UUID projectId,
        long expectedVersion,
        ProjectVisibility visibility
) {
}
