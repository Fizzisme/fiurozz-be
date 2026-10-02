package com.philia.projectservice.catalog.internal.adapter.in.web.dto.request;

import jakarta.validation.constraints.Pattern;

public record PublishProjectRequest(
        @Pattern(
                regexp = "^(PUBLIC|UNLISTED|PRIVATE)$",
                flags = Pattern.Flag.CASE_INSENSITIVE,
                message = "visibility must be PUBLIC, UNLISTED, or PRIVATE"
        )
        String visibility
) {
}
