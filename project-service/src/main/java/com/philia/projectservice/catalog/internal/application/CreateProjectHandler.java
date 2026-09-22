package com.philia.projectservice.catalog.internal.application;

import com.philia.projectservice.catalog.api.CreateProjectCommand;
import com.philia.projectservice.catalog.api.CreateProjectUseCase;
import com.philia.projectservice.catalog.api.ProjectDetailResult;
import com.philia.projectservice.catalog.internal.application.exception.ProjectSlugAlreadyExistsException;
import com.philia.projectservice.catalog.internal.application.exception.SubCategoryUnavailableException;
import com.philia.projectservice.catalog.internal.application.exception.TagsUnavailableException;
import com.philia.projectservice.catalog.internal.application.port.out.CatalogReferenceQuery;
import com.philia.projectservice.catalog.internal.application.port.out.CurrentActor;
import com.philia.projectservice.catalog.internal.application.port.out.ProjectRepository;
import com.philia.projectservice.catalog.internal.application.port.out.ProjectTagRepository;
import com.philia.projectservice.catalog.internal.domain.Project;
import com.philia.projectservice.catalog.internal.domain.ProjectSlug;
import com.philia.projectservice.catalog.internal.domain.ProjectVisibility;
import com.philia.projectservice.catalog.internal.domain.exception.InvalidProjectException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final ProjectTagRepository projectTagRepository;
    private final CatalogReferenceQuery catalogReferenceQuery;
    private final Clock clock;

    public CreateProjectHandler(
            CurrentActor currentActor,
            ProjectRepository projectRepository,
            ProjectTagRepository projectTagRepository,
            CatalogReferenceQuery catalogReferenceQuery,
            Clock clock
    ) {
        this.currentActor = currentActor;
        this.projectRepository = projectRepository;
        this.projectTagRepository = projectTagRepository;
        this.catalogReferenceQuery = catalogReferenceQuery;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ProjectDetailResult create(CreateProjectCommand command) {
        if (command == null) {
            throw new InvalidProjectException("Create project command is required");
        }

        var actor = currentActor.getRequiredActor();
        var slug = ProjectSlug.fromTitle(command.title());
        var visibility = ProjectVisibility.fromNullable(command.visibility());
        var tagIds = uniqueTagIds(command.tagIds());

        var subCategory = catalogReferenceQuery.findActiveSubCategory(command.subCategoryId())
                .orElseThrow(() -> new SubCategoryUnavailableException(command.subCategoryId()));
        var tags = loadAllActiveTags(tagIds);

        if (projectRepository.existsActiveSlug(actor.id(), slug)) {
            throw new ProjectSlugAlreadyExistsException(slug.value());
        }

        var now = clock.instant();
        var project = Project.create(
                UUID.randomUUID(),
                actor.id(),
                actor.displayName(),
                actor.avatarUrl(),
                subCategory.id(),
                command.title(),
                slug,
                command.shortDescription(),
                command.description(),
                command.demoUrl(),
                normalizedItems(command.techStack(), MAX_TECH_STACK_ITEMS, 60, true, "techStack"),
                normalizedItems(command.features(), MAX_FEATURE_ITEMS, 200, false, "features"),
                visibility,
                now
        );

        projectRepository.save(project);
        projectTagRepository.addAll(project.id(), tagIds);

        return toResult(project, subCategory, tags);
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
            List<CatalogReferenceQuery.TagReference> tags
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
                project.demoUrl(),
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
