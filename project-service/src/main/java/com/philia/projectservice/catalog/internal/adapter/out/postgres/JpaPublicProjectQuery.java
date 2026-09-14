package com.philia.projectservice.catalog.internal.adapter.out.postgres;

import com.philia.projectservice.catalog.api.ProjectCursorResult;
import com.philia.projectservice.catalog.api.PublicProjectCardResult;
import com.philia.projectservice.catalog.api.PublicProjectCursor;
import com.philia.projectservice.catalog.api.PublicProjectSearchQuery;
import com.philia.projectservice.catalog.internal.application.port.out.PublicProjectQuery;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.Locale;
import java.util.UUID;

@Repository
public class JpaPublicProjectQuery implements PublicProjectQuery {
    private final JpaProjectListRepository repository;

    public JpaPublicProjectQuery(JpaProjectListRepository repository) { this.repository = repository; }

    @Override
    public ProjectCursorResult<PublicProjectCardResult> search(PublicProjectSearchQuery query) {
        var direction = query.sort() == PublicProjectSearchQuery.SortOrder.NEWEST
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        var sort = Sort.by(direction, "publishedAt").and(Sort.by(direction, "id"));
        var candidates = repository.findAll(
                specification(query),
                PageRequest.of(0, query.limit() + 1, sort)
        ).getContent();
        var hasMore = candidates.size() > query.limit();
        var projects = candidates.stream().limit(query.limit()).toList();
        var nextCursor = hasMore && !projects.isEmpty()
                ? cursorFor(projects.getLast())
                : null;

        return new ProjectCursorResult<>(projects.stream().map(this::card).toList(), nextCursor, hasMore);
    }

    private static Specification<ProjectJpaEntity> specification(PublicProjectSearchQuery query) {
        Specification<ProjectJpaEntity> spec = (root, ignored, cb) -> cb.and(
                cb.equal(root.get("status"), "PUBLISHED"), cb.equal(root.get("visibility"), "PUBLIC"),
                cb.isNull(root.get("deletedAt")));
        if (query.ownerId() != null) spec = spec.and((root, ignored, cb) -> cb.equal(root.get("ownerId"), query.ownerId()));
        if (query.subCategoryId() != null) spec = spec.and((root, ignored, cb) -> cb.equal(root.get("subCategory").get("id"), query.subCategoryId()));
        if (query.categoryId() != null) spec = spec.and((root, ignored, cb) -> cb.equal(
                root.join("subCategory", JoinType.INNER).get("category").get("id"), query.categoryId()));
        if (query.subCategorySlug() != null) spec = spec.and((root, ignored, cb) -> cb.equal(
                root.get("subCategory").get("slug"), query.subCategorySlug()));
        if (query.categorySlug() != null) spec = spec.and((root, ignored, cb) -> cb.equal(
                root.join("subCategory", JoinType.INNER).get("category").get("slug"), query.categorySlug()));
        if (query.tag() != null) spec = spec.and((root, criteria, cb) -> {
            criteria.distinct(true);
            return cb.equal(root.join("tags", JoinType.INNER).get("slug"), query.tag().toLowerCase(Locale.ROOT));
        });
        if (query.search() != null) spec = spec.and((root, ignored, cb) -> {
            var pattern = "%" + query.search().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            return cb.or(cb.like(cb.lower(root.get("title")), pattern, '\\'),
                    cb.like(cb.lower(root.get("shortDescription")), pattern, '\\'));
        });
        if (query.cursor() != null) {
            spec = spec.and(cursorSpecification(query.cursor(), query.sort()));
        }
        return spec;
    }

    private static Specification<ProjectJpaEntity> cursorSpecification(
            PublicProjectCursor cursor,
            PublicProjectSearchQuery.SortOrder sort
    ) {
        return (root, ignored, cb) -> {
            var publishedAt = root.<java.time.Instant>get("publishedAt");
            var projectId = root.<UUID>get("id");
            if (sort == PublicProjectSearchQuery.SortOrder.NEWEST) {
                return cb.or(
                        cb.lessThan(publishedAt, cursor.publishedAt()),
                        cb.and(
                                cb.equal(publishedAt, cursor.publishedAt()),
                                cb.lessThan(projectId, cursor.projectId())
                        )
                );
            }
            return cb.or(
                    cb.greaterThan(publishedAt, cursor.publishedAt()),
                    cb.and(
                            cb.equal(publishedAt, cursor.publishedAt()),
                            cb.greaterThan(projectId, cursor.projectId())
                    )
            );
        };
    }

    private static PublicProjectCursor cursorFor(ProjectJpaEntity project) {
        return new PublicProjectCursor(project.getPublishedAt(), project.getId());
    }

    private PublicProjectCardResult card(ProjectJpaEntity project) {
        var subCategory = project.getSubCategory();
        var category = subCategory.getCategory();
        return new PublicProjectCardResult(
                project.getId(),
                project.getTitle(),
                project.getSlug(),
                project.getShortDescription(),
                project.getThumbnailUrl(),
                new PublicProjectCardResult.Category(category.getId(), category.getSlug(), category.getTitle()),
                new PublicProjectCardResult.SubCategory(subCategory.getId(), subCategory.getSlug(), subCategory.getTitle()),
                project.getTechStack(),
                new PublicProjectCardResult.Owner(
                        project.getOwnerId(), project.getOwnerDisplayName(), project.getOwnerAvatarUrl()),
                project.getPublishedAt(),
                project.getCreatedAt()
        );
    }
}
