package com.philia.projectservice.catalog.internal.adapter.out.postgres;

import com.philia.projectservice.catalog.api.ProjectPageResult;
import com.philia.projectservice.catalog.api.ProjectSummaryResult;
import com.philia.projectservice.catalog.api.PublicProjectSearchQuery;
import com.philia.projectservice.catalog.internal.application.port.out.PublicProjectQuery;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.Locale;

@Repository
public class JpaPublicProjectQuery implements PublicProjectQuery {
    private final JpaProjectListRepository repository;

    public JpaPublicProjectQuery(JpaProjectListRepository repository) { this.repository = repository; }

    @Override
    public ProjectPageResult<ProjectSummaryResult> search(PublicProjectSearchQuery query) {
        var sort = Sort.by(query.sort() == PublicProjectSearchQuery.SortOrder.NEWEST ? Sort.Direction.DESC : Sort.Direction.ASC,
                "publishedAt");
        var page = repository.findAll(specification(query), PageRequest.of(query.page(), query.size(), sort));
        return new ProjectPageResult<>(page.getContent().stream().map(this::summary).toList(), page.getNumber(),
                page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    private static Specification<ProjectJpaEntity> specification(PublicProjectSearchQuery query) {
        Specification<ProjectJpaEntity> spec = (root, ignored, cb) -> cb.and(
                cb.equal(root.get("status"), "PUBLISHED"), cb.equal(root.get("visibility"), "PUBLIC"),
                cb.isNull(root.get("deletedAt")));
        if (query.ownerId() != null) spec = spec.and((root, ignored, cb) -> cb.equal(root.get("ownerId"), query.ownerId()));
        if (query.subCategoryId() != null) spec = spec.and((root, ignored, cb) -> cb.equal(root.get("subCategory").get("id"), query.subCategoryId()));
        if (query.categoryId() != null) spec = spec.and((root, ignored, cb) -> cb.equal(
                root.join("subCategory", JoinType.INNER).get("category").get("id"), query.categoryId()));
        if (query.tag() != null) spec = spec.and((root, criteria, cb) -> {
            criteria.distinct(true);
            return cb.equal(root.join("tags", JoinType.INNER).get("slug"), query.tag().toLowerCase(Locale.ROOT));
        });
        if (query.search() != null) spec = spec.and((root, ignored, cb) -> {
            var pattern = "%" + query.search().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            return cb.or(cb.like(cb.lower(root.get("title")), pattern, '\\'),
                    cb.like(cb.lower(root.get("shortDescription")), pattern, '\\'));
        });
        return spec;
    }

    private ProjectSummaryResult summary(ProjectJpaEntity project) {
        return new ProjectSummaryResult(project.getId(), project.getTitle(), project.getSlug(), project.getShortDescription(),
                project.getThumbnailUrl(), project.getStatus(), project.getVisibility(), project.getPublishedAt(),
                project.getCreatedAt(), project.getUpdatedAt(), project.getVersion());
    }
}
