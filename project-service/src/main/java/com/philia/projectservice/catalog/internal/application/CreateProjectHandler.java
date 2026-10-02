package com.philia.projectservice.catalog.internal.application;

import com.philia.projectservice.catalog.api.CreateProjectCommand;
import com.philia.projectservice.catalog.api.CreateProjectUseCase;
import com.philia.projectservice.catalog.api.ProjectDetailResult;
import com.philia.projectservice.catalog.internal.application.exception.ProjectSlugAlreadyExistsException;
import com.philia.projectservice.catalog.internal.application.exception.SubCategoryUnavailableException;
import com.philia.projectservice.catalog.internal.application.exception.TagsUnavailableException;
import com.philia.projectservice.catalog.internal.application.port.out.CatalogReferenceQuery;
import com.philia.projectservice.catalog.internal.application.port.out.CurrentActor;
import com.philia.projectservice.catalog.internal.application.port.out.ProjectMediaRepository.NewProjectMedia;
import com.philia.projectservice.catalog.internal.application.port.out.ProjectMediaStorage;
import com.philia.projectservice.catalog.internal.application.port.out.ProjectRepository;
import com.philia.projectservice.catalog.internal.domain.Project;
import com.philia.projectservice.catalog.internal.domain.ProjectMediaType;
import com.philia.projectservice.catalog.internal.domain.ProjectSlug;
import com.philia.projectservice.catalog.internal.domain.ProjectVisibility;
import com.philia.projectservice.catalog.internal.domain.exception.InvalidProjectException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class CreateProjectHandler implements CreateProjectUseCase {

    private static final int MAX_TECH_STACK_ITEMS = 20;
    private static final int MAX_FEATURE_ITEMS = 30;
    private static final int MAX_TAGS = 10;

    private final CurrentActor currentActor;
    private final ProjectRepository projectRepository;
    private final CatalogReferenceQuery catalogReferenceQuery;
    private final ProjectMediaStorage mediaStorage;
    private final CreateProjectWriter writer;
    private final Clock clock;

    public CreateProjectHandler(
            CurrentActor currentActor,
            ProjectRepository projectRepository,
            CatalogReferenceQuery catalogReferenceQuery,
            ProjectMediaStorage mediaStorage,
            CreateProjectWriter writer,
            Clock clock
    ) {
        this.currentActor = currentActor;
        this.projectRepository = projectRepository;
        this.catalogReferenceQuery = catalogReferenceQuery;
        this.mediaStorage = mediaStorage;
        this.writer = writer;
        this.clock = clock;
    }

    /**
     * Not transactional: media is uploaded first, then {@link CreateProjectWriter} persists the project
     * in its own transaction. Uploaded objects are deleted again if anything after the upload fails.
     */
    @Override
    public ProjectDetailResult create(CreateProjectCommand command) {
        if (command == null) {
            throw new InvalidProjectException("Create project command is required");
        }

        var actor = currentActor.getRequiredActor();
        var slug = ProjectSlug.fromTitle(command.title());
        var visibility = ProjectVisibility.fromNullable(command.visibility());
        var tagIds = uniqueTagIds(command.tagIds());
        var validatedMedia = ProjectMediaPolicy.validate(command.images(), command.video());

        var subCategory = catalogReferenceQuery.findActiveSubCategory(command.subCategoryId())
                .orElseThrow(() -> new SubCategoryUnavailableException(command.subCategoryId()));
        var tags = loadAllActiveTags(tagIds);

        if (projectRepository.existsActiveSlug(actor.id(), slug)) {
            throw new ProjectSlugAlreadyExistsException(slug.value());
        }

        var projectId = UUID.randomUUID();
        var media = planStorage(projectId, validatedMedia);
        var now = clock.instant();
        // Built before uploading so invalid project fields fail without touching storage.
        var project = Project.create(
                projectId,
                actor.id(),
                actor.displayName(),
                actor.avatarUrl(),
                subCategory.id(),
                command.title(),
                slug,
                command.shortDescription(),
                command.description(),
                media.getFirst().url(),
                command.demoUrl(),
                command.githubUrl(),
                normalizedItems(command.techStack(), MAX_TECH_STACK_ITEMS, 60, true, "techStack"),
                normalizedItems(command.features(), MAX_FEATURE_ITEMS, 200, false, "features"),
                visibility,
                now
        );

        upload(validatedMedia, media);
        try {
            writer.write(project, tagIds, media);
        } catch (RuntimeException exception) {
            discard(media, exception);
            throw exception;
        }

        return toResult(project, subCategory, tags, media);
    }

    private List<NewProjectMedia> planStorage(UUID projectId, List<ProjectMediaPolicy.ValidatedMedia> validated) {
        return validated.stream()
                .map(item -> {
                    var objectKey = "projects/" + projectId + "/" + UUID.randomUUID() + "." + item.extension();
                    return new NewProjectMedia(
                            UUID.randomUUID(),
                            item.type(),
                            mediaStorage.publicUrl(objectKey),
                            objectKey,
                            item.contentType(),
                            item.upload().sizeBytes(),
                            item.sortOrder()
                    );
                })
                .toList();
    }

    private void upload(List<ProjectMediaPolicy.ValidatedMedia> validated, List<NewProjectMedia> media) {
        var uploaded = new ArrayList<NewProjectMedia>(media.size());
        try {
            for (var index = 0; index < media.size(); index++) {
                var target = media.get(index);
                try (var content = validated.get(index).upload().content().open()) {
                    mediaStorage.put(target.objectKey(), target.contentType(), target.sizeBytes(), content);
                } catch (IOException exception) {
                    throw new UncheckedIOException("Failed to read uploaded media", exception);
                }
                uploaded.add(target);
            }
        } catch (RuntimeException exception) {
            discard(uploaded, exception);
            throw exception;
        }
    }

    private void discard(List<NewProjectMedia> media, RuntimeException failure) {
        try {
            mediaStorage.deleteAll(media.stream().map(NewProjectMedia::objectKey).toList());
        } catch (RuntimeException cleanupFailure) {
            // Keep the original error; the orphaned objects stay in storage.
            failure.addSuppressed(cleanupFailure);
        }
    }

    private List<CatalogReferenceQuery.TagReference> loadAllActiveTags(Set<UUID> tagIds) {
        if (tagIds.isEmpty()) {
            return List.of();
        }

        var tags = catalogReferenceQuery.findActiveTags(tagIds);
        var foundIds = new LinkedHashSet<UUID>();
        tags.forEach(tag -> foundIds.add(tag.id()));

        var unavailable = new LinkedHashSet<>(tagIds);
        unavailable.removeAll(foundIds);
        if (!unavailable.isEmpty()) {
            throw new TagsUnavailableException(unavailable);
        }
        return tags;
    }

    private static Set<UUID> uniqueTagIds(List<UUID> values) {
        var tagIds = new LinkedHashSet<UUID>();
        if (values != null) {
            for (var value : values) {
                if (value == null) {
                    throw new InvalidProjectException("tagIds must not contain null values");
                }
                tagIds.add(value);
            }
        }
        if (tagIds.size() > MAX_TAGS) {
            throw new InvalidProjectException("A project may have at most 10 tags");
        }
        return tagIds;
    }

    private static List<String> normalizedItems(
            List<String> values,
            int maximumItems,
            int maximumLength,
            boolean lowercase,
            String fieldName
    ) {
        if (values == null) {
            return List.of();
        }
        if (values.size() > maximumItems) {
            throw new InvalidProjectException(fieldName + " contains too many items");
        }

        var unique = new LinkedHashMap<String, String>();
        for (var value : values) {
            if (value == null || value.isBlank()) {
                throw new InvalidProjectException(fieldName + " must not contain blank items");
            }
            var trimmed = value.trim();
            if (trimmed.length() > maximumLength) {
                throw new InvalidProjectException(fieldName + " item exceeds its maximum length");
            }
            var normalized = trimmed.toLowerCase(Locale.ROOT);
            if (lowercase) {
                normalized = normalized.replaceAll("[\\s_]+", "-").replaceAll("-+", "-");
            }
            unique.putIfAbsent(normalized, lowercase ? normalized : trimmed);
        }
        return new ArrayList<>(unique.values());
    }

    private static ProjectDetailResult toResult(
            Project project,
            CatalogReferenceQuery.SubCategoryReference subCategory,
            List<CatalogReferenceQuery.TagReference> tags,
            List<NewProjectMedia> media
    ) {
        var category = subCategory.category();
        return new ProjectDetailResult(
                project.id(),
                new ProjectDetailResult.Owner(
                        project.ownerId(),
                        project.ownerDisplayName(),
                        project.ownerAvatarUrl()
                ),
                new ProjectDetailResult.Category(
                        category.id(),
                        category.key(),
                        category.slug(),
                        category.title(),
                        category.icon()
                ),
                new ProjectDetailResult.SubCategory(
                        subCategory.id(),
                        subCategory.key(),
                        subCategory.slug(),
                        subCategory.title()
                ),
                project.title(),
                project.slug().value(),
                project.shortDescription(),
                project.description(),
                project.thumbnailUrl(),
                // Same rule as the detail query: only IMAGE media are listed in images.
                media.stream()
                        .filter(item -> item.type() == ProjectMediaType.IMAGE)
                        .map(NewProjectMedia::url)
                        .toList(),
                media.stream()
                        .map(item -> new ProjectDetailResult.Media(
                                item.id(), item.type().name(), item.url(), item.sortOrder()))
                        .toList(),
                project.demoUrl(),
                project.repositoryUrl(),
                project.techStack(),
                project.features(),
                tags.stream()
                        .map(tag -> new ProjectDetailResult.Tag(tag.id(), tag.slug(), tag.displayName()))
                        .toList(),
                project.status().name(),
                project.visibility().name(),
                project.sourceVisibility().name(),
                new ProjectDetailResult.Statistics(
                        project.viewCount(),
                        project.likeCount(),
                        project.commentCount()
                ),
                project.publishedAt(),
                project.createdAt(),
                project.updatedAt(),
                project.version()
        );
    }
}
