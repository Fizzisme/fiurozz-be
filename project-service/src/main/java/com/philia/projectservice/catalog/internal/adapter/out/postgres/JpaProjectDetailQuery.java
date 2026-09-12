package com.philia.projectservice.catalog.internal.adapter.out.postgres;

import com.philia.projectservice.catalog.api.ProjectDetailResult;
import com.philia.projectservice.catalog.internal.application.port.out.ProjectDetailQuery;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA read adapter for the complete project detail projection.
 */
@Repository
public class JpaProjectDetailQuery implements ProjectDetailQuery {

    private final JpaProjectDetailRepository projectRepository;
    private final JpaProjectMediaRepository mediaRepository;
    private final JpaProjectRepositoryLinkRepository repositoryLinkRepository;

    public JpaProjectDetailQuery(
            JpaProjectDetailRepository projectRepository,
            JpaProjectMediaRepository mediaRepository,
            JpaProjectRepositoryLinkRepository repositoryLinkRepository
    ) {
        this.projectRepository = projectRepository;
        this.mediaRepository = mediaRepository;
        this.repositoryLinkRepository = repositoryLinkRepository;
    }

    @Override
    public Optional<ProjectDetailResult> findActiveById(UUID projectId) {
        if (projectId == null) {
            return Optional.empty();
        }
        return projectRepository.findActiveDetailById(projectId).map(this::toResult);
    }

    @Override
    public Optional<ProjectDetailResult> findPublicByOwnerAndSlug(UUID ownerId, String slug) {
        if (ownerId == null || slug == null || slug.isBlank()) return Optional.empty();
        return projectRepository.findPublicDetailByOwnerAndSlug(ownerId, slug.trim()).map(this::toResult);
    }

    private ProjectDetailResult toResult(ProjectJpaEntity project) {
        var subCategory = project.getSubCategory();
        var category = subCategory.getCategory();
        var tags = project.getTags().stream()
                .map(tag -> new ProjectDetailResult.Tag(tag.getId(), tag.getSlug(), tag.getDisplayName()))
                .sorted(Comparator.comparing(ProjectDetailResult.Tag::displayName)
                        .thenComparing(ProjectDetailResult.Tag::id))
                .toList();

        return new ProjectDetailResult(
                project.getId(),
                new ProjectDetailResult.Owner(
                        project.getOwnerId(), project.getOwnerDisplayName(), project.getOwnerAvatarUrl()),
                new ProjectDetailResult.Category(
                        category.getId(), category.getKey(), category.getSlug(), category.getTitle(), category.getIcon()),
                new ProjectDetailResult.SubCategory(
                        subCategory.getId(), subCategory.getKey(), subCategory.getSlug(), subCategory.getTitle()),
                project.getTitle(),
                project.getSlug(),
                project.getShortDescription(),
                project.getDescription(),
                project.getThumbnailUrl(),
                mediaRepository.findImageUrlsByProjectId(project.getId()),
                project.getDemoUrl(),
                repositoryLinkRepository.findPrimaryRepositoryUrl(project.getId()).orElse(null),
                project.getTechStack(),
                project.getFeatures(),
                tags,
                project.getStatus(),
                project.getVisibility(),
                project.getSourceVisibility(),
                new ProjectDetailResult.Statistics(
                        project.getViewCount(), project.getLikeCount(), project.getCommentCount()),
                project.getPublishedAt(),
                project.getCreatedAt(),
                project.getUpdatedAt(),
                project.getVersion()
        );
    }

}
