package com.philia.projectservice.catalog.internal.domain;

import com.philia.projectservice.catalog.internal.domain.exception.InvalidProjectException;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class Project {

    private static final int MAX_TITLE_LENGTH = 180;
    private static final int MAX_SHORT_DESCRIPTION_LENGTH = 500;
    private static final int MAX_DEMO_URL_LENGTH = 500;
    private static final int MAX_REPOSITORY_URL_LENGTH = 500;

    private final UUID id;
    private final UUID ownerId;
    private final String ownerDisplayName;
    private final String ownerAvatarUrl;
    private final UUID subCategoryId;
    private final String title;
    private final ProjectSlug slug;
    private final String shortDescription;
    private final String description;
    private final String thumbnailUrl;
    private final String demoUrl;
    private final String repositoryUrl;
    private final List<String> techStack;
    private final List<String> features;
    private final ProjectStatus status;
    private final ProjectVisibility visibility;
    private final SourceVisibility sourceVisibility;
    private final long viewCount;
    private final long likeCount;
    private final long commentCount;
    private final Instant publishedAt;
    private final Instant deletedAt;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final long version;

    private Project(
            UUID id,
            UUID ownerId,
            String ownerDisplayName,
            String ownerAvatarUrl,
            UUID subCategoryId,
            String title,
            ProjectSlug slug,
            String shortDescription,
            String description,
            String thumbnailUrl,
            String demoUrl,
            String repositoryUrl,
            List<String> techStack,
            List<String> features,
            ProjectStatus status,
            ProjectVisibility visibility,
            SourceVisibility sourceVisibility,
            long viewCount,
            long likeCount,
            long commentCount,
            Instant publishedAt,
            Instant deletedAt,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        this.id = Objects.requireNonNull(id, "Project ID is required");
        this.ownerId = Objects.requireNonNull(ownerId, "Project owner ID is required");
        this.ownerDisplayName = requiredText(ownerDisplayName, 120, "Owner display name");
        this.ownerAvatarUrl = optionalText(ownerAvatarUrl, 500, "Owner avatar URL");
        this.subCategoryId = Objects.requireNonNull(subCategoryId, "Project subcategory ID is required");
        this.title = requiredText(title, MAX_TITLE_LENGTH, "Project title");
        this.slug = Objects.requireNonNull(slug, "Project slug is required");
        this.shortDescription = requiredText(
                shortDescription,
                MAX_SHORT_DESCRIPTION_LENGTH,
                "Project short description"
        );
        this.description = requiredText(description, Integer.MAX_VALUE, "Project description");
        this.thumbnailUrl = optionalText(thumbnailUrl, 500, "Project thumbnail URL");
        this.demoUrl = optionalText(demoUrl, MAX_DEMO_URL_LENGTH, "Project demo URL");
        this.repositoryUrl = optionalText(repositoryUrl, MAX_REPOSITORY_URL_LENGTH, "Project repository URL");
        this.techStack = List.copyOf(techStack == null ? List.of() : techStack);
        this.features = List.copyOf(features == null ? List.of() : features);
        this.status = Objects.requireNonNull(status, "Project status is required");
        this.visibility = Objects.requireNonNull(visibility, "Project visibility is required");
        this.sourceVisibility = Objects.requireNonNull(sourceVisibility, "Source visibility is required");
        this.viewCount = nonNegative(viewCount, "viewCount");
        this.likeCount = nonNegative(likeCount, "likeCount");
        this.commentCount = nonNegative(commentCount, "commentCount");
        this.publishedAt = publishedAt;
        this.deletedAt = deletedAt;
        this.createdAt = Objects.requireNonNull(createdAt, "Project creation time is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "Project update time is required");
        this.version = nonNegative(version, "version");
    }

    public static Project create(
            UUID id,
            UUID ownerId,
            String ownerDisplayName,
            String ownerAvatarUrl,
            UUID subCategoryId,
            String title,
            ProjectSlug slug,
            String shortDescription,
            String description,
            String demoUrl,
            String repositoryUrl,
            List<String> techStack,
            List<String> features,
            ProjectVisibility visibility,
            Instant now
    ) {
        return new Project(
                id,
                ownerId,
                ownerDisplayName,
                ownerAvatarUrl,
                subCategoryId,
                title,
                slug,
                shortDescription,
                description,
                null,
                demoUrl,
                repositoryUrl,
                techStack,
                features,
                ProjectStatus.DRAFT,
                visibility,
                SourceVisibility.HIDDEN,
                0,
                0,
                0,
                null,
                null,
                now,
                now,
                0
        );
    }

    private static String requiredText(String value, int maximumLength, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InvalidProjectException(fieldName + " must not be blank");
        }
        return optionalText(value, maximumLength, fieldName);
    }

    private static String optionalText(String value, int maximumLength, String fieldName) {
        if (value == null) {
            return null;
        }
        var trimmed = value.trim();
        if (trimmed.length() > maximumLength) {
            throw new InvalidProjectException(fieldName + " exceeds its maximum length");
        }
        return trimmed;
    }

    private static long nonNegative(long value, String fieldName) {
        if (value < 0) {
            throw new InvalidProjectException(fieldName + " must not be negative");
        }
        return value;
    }

    public UUID id() {
        return id;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public String ownerDisplayName() {
        return ownerDisplayName;
    }

    public String ownerAvatarUrl() {
        return ownerAvatarUrl;
    }

    public UUID subCategoryId() {
        return subCategoryId;
    }

    public String title() {
        return title;
    }

    public ProjectSlug slug() {
        return slug;
    }

    public String shortDescription() {
        return shortDescription;
    }

    public String description() {
        return description;
    }

    public String thumbnailUrl() {
        return thumbnailUrl;
    }

    public String demoUrl() {
        return demoUrl;
    }

    public String repositoryUrl() {
        return repositoryUrl;
    }

    public List<String> techStack() {
        return techStack;
    }

    public List<String> features() {
        return features;
    }

    public ProjectStatus status() {
        return status;
    }

    public ProjectVisibility visibility() {
        return visibility;
    }

    public SourceVisibility sourceVisibility() {
        return sourceVisibility;
    }

    public long viewCount() {
        return viewCount;
    }

    public long likeCount() {
        return likeCount;
    }

    public long commentCount() {
        return commentCount;
    }

    public Instant publishedAt() {
        return publishedAt;
    }

    public Instant deletedAt() {
        return deletedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public long version() {
        return version;
    }
}
