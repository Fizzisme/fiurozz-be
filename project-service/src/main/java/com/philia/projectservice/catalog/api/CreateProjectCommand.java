package com.philia.projectservice.catalog.api;

import java.util.List;
import java.util.UUID;

public record CreateProjectCommand(
        UUID subCategoryId,
        String title,
        String shortDescription,
        String description,
        String demoUrl,
        String visibility,
        List<String> techStack,
        List<String> features,
        List<UUID> tagIds
) {
}
