package com.philia.projectservice.catalog.internal.adapter.in.web.dto.request;

import com.philia.projectservice.catalog.internal.domain.ProjectVisibility;
import jakarta.validation.constraints.NotNull;

public record ChangeProjectVisibilityRequest(
        @NotNull(message = "visibility is required") ProjectVisibility visibility
) {
}
