package com.philia.projectservice.catalog.internal.adapter.out.postgres;

import com.philia.projectservice.catalog.api.ListMyProjectsQuery;
import com.philia.projectservice.catalog.api.ProjectPageResult;
import com.philia.projectservice.catalog.api.ProjectSummaryResult;
import com.philia.projectservice.catalog.internal.application.port.out.ProjectSummaryQuery;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.Locale;
import java.util.UUID;

/**
 * Spring Data JPA query adapter for an owner's paginated project dashboard.
 */
@Repository
public class JpaProjectSummaryQuery implements ProjectSummaryQuery {

    private final JpaProjectListRepository projectRepository;

    public JpaProjectSummaryQuery(JpaProjectListRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Override
    public ProjectPageResult<ProjectSummaryResult> findActiveByOwner(UUID ownerId, ListMyProjectsQuery query) {
        var pageable = PageRequest.of(
                query.page(),
                query.size(),
                Sort.by(Sort.Direction.valueOf(query.sort().direction()), query.sort().property())
        );
        var projects = projectRepository.findAll(specification(ownerId, query), pageable);

        return new ProjectPageResult<>(
                projects.getContent().stream().map(this::toSummary).toList(),
                projects.getNumber(),
                projects.getSize(),
                projects.getTotalElements(),
                projects.getTotalPages()
        );
    }

    private static Specification<ProjectJpaEntity> specification(UUID ownerId, ListMyProjectsQuery query) {
        Specification<ProjectJpaEntity> specification = (root, criteriaQuery, criteriaBuilder) -> criteriaBuilder.and(
                criteriaBuilder.equal(root.get("ownerId"), ownerId),
                criteriaBuilder.isNull(root.get("deletedAt"))
        );

        if (query.status() != null) {
            specification = specification.and((root, criteriaQuery, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("status"), query.status().name()));
        }
        if (query.visibility() != null) {
            specification = specification.and((root, criteriaQuery, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("visibility"), query.visibility().name()));
        }
        if (query.search() != null) {
            var pattern = '%' + escapeLikePattern(query.search().toLowerCase(Locale.ROOT)) + '%';
            specification = specification.and((root, criteriaQuery, criteriaBuilder) -> criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), pattern, '\\'),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("shortDescription")), pattern, '\\'),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), pattern, '\\')
            ));
        }
        return specification;
    }

    private static String escapeLikePattern(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private ProjectSummaryResult toSummary(ProjectJpaEntity project) {
        return new ProjectSummaryResult(
                project.getId(),
                project.getTitle(),
                project.getSlug(),
                project.getShortDescription(),
                project.getThumbnailUrl(),
                project.getStatus(),
                project.getVisibility(),
                project.getPublishedAt(),
                project.getCreatedAt(),
                project.getUpdatedAt(),
                project.getVersion()
        );
    }
}
