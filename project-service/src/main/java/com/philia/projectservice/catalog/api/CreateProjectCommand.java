package com.philia.projectservice.catalog.api;

import java.util.List;
import java.util.UUID;

/**
 * @param images 3-5 images in display order; the first one becomes the thumbnail
 * @param video  optional demo video, may be {@code null}
 */
public record CreateProjectCommand(
        UUID subCategoryId,
        String title,
        String shortDescription,
        String description,
        String demoUrl,
        String githubUrl,
        String visibility,
        List<String> techStack,
        List<String> features,
        List<UUID> tagIds,
        List<ProjectMediaUpload> images,
        ProjectMediaUpload video
) {
}
