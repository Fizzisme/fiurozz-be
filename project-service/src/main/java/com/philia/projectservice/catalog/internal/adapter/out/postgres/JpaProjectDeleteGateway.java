package com.philia.projectservice.catalog.internal.adapter.out.postgres;

import com.philia.projectservice.catalog.internal.application.port.out.ProjectDeleteGateway;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaProjectDeleteGateway implements ProjectDeleteGateway {

    private final JpaProjectCommandRepository projectRepository;

    public JpaProjectDeleteGateway(JpaProjectCommandRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Override
    public Optional<ProjectState> findActiveState(UUID projectId) {
        return projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .map(project -> new ProjectState(project.getOwnerId(), project.getStatus(), project.getVersion()));
    }

    @Override
    public boolean softDeleteIfCurrent(UUID projectId, UUID ownerId, long expectedVersion, Instant deletedAt) {
        return projectRepository.softDeleteIfCurrent(projectId, ownerId, expectedVersion, deletedAt) == 1;
    }
}
