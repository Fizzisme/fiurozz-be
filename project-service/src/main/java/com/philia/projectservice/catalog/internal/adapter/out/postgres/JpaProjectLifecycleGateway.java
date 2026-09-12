package com.philia.projectservice.catalog.internal.adapter.out.postgres;

import com.philia.projectservice.catalog.internal.application.port.out.ProjectLifecycleGateway;
import com.philia.projectservice.catalog.internal.domain.ProjectVisibility;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaProjectLifecycleGateway implements ProjectLifecycleGateway {
    private final JpaProjectCommandRepository projectRepository;

    public JpaProjectLifecycleGateway(JpaProjectCommandRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Override
    public Optional<ProjectState> findActiveState(UUID projectId) {
        return projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .map(project -> new ProjectState(project.getOwnerId(), project.getStatus(), project.getVersion()));
    }

    @Override
    public boolean archiveIfCurrent(UUID projectId, UUID ownerId, long expectedVersion, Instant updatedAt) {
        return projectRepository.archiveIfCurrent(projectId, ownerId, expectedVersion, updatedAt) == 1;
    }

    @Override
    public boolean reopenIfCurrent(UUID projectId, UUID ownerId, long expectedVersion, Instant updatedAt) {
        return projectRepository.reopenIfCurrent(projectId, ownerId, expectedVersion, updatedAt) == 1;
    }

    @Override
    public boolean changeVisibilityIfCurrent(UUID projectId, UUID ownerId, long expectedVersion,
                                             ProjectVisibility visibility, Instant updatedAt) {
        return projectRepository.changeVisibilityIfCurrent(
                projectId, ownerId, expectedVersion, visibility.name(), updatedAt) == 1;
    }
}
