package com.philia.projectservice.catalog.internal.adapter.out.postgres;

import com.philia.projectservice.catalog.internal.application.port.out.ProjectPublicationGateway;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.UUID;

/** JPA adapter that reads publication prerequisites and performs the atomic transition. */
@Repository
public class JpaProjectPublicationGateway implements ProjectPublicationGateway {

    private final JpaProjectCommandRepository projectRepository;

    public JpaProjectPublicationGateway(JpaProjectCommandRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Override
    public Optional<ProjectState> findActiveState(UUID projectId) {
        return projectRepository.findByIdAndDeletedAtIsNull(projectId).map(project -> new ProjectState(
                project.getOwnerId(),
                project.getStatus(),
                project.getVersion(),
                project.getSubCategory().getId(),
                project.getTags().stream()
                        .map(ProjectTagJpaEntity::getId)
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)),
                project.getTitle(),
                project.getSlug(),
                project.getShortDescription(),
                project.getDescription(),
                project.getDemoUrl()
        ));
    }

    @Override
    public boolean publishIfCurrent(UUID projectId, UUID ownerId, long expectedVersion, Instant publishedAt) {
        return projectRepository.publishDraftIfCurrent(projectId, ownerId, expectedVersion, publishedAt) == 1;
    }
}
