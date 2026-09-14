package com.philia.projectservice.catalog.internal.application;

import com.philia.projectservice.catalog.api.DeleteProjectCommand;
import com.philia.projectservice.catalog.api.DeleteProjectUseCase;
import com.philia.projectservice.catalog.internal.application.exception.ProjectForbiddenException;
import com.philia.projectservice.catalog.internal.application.exception.ProjectNotDeletableException;
import com.philia.projectservice.catalog.internal.application.exception.ProjectNotFoundException;
import com.philia.projectservice.catalog.internal.application.exception.ProjectStaleVersionException;
import com.philia.projectservice.catalog.internal.application.port.out.CurrentActor;
import com.philia.projectservice.catalog.internal.application.port.out.ProjectDeleteGateway;
import com.philia.projectservice.catalog.internal.domain.exception.InvalidProjectException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class DeleteProjectHandler implements DeleteProjectUseCase {

    private final CurrentActor currentActor;
    private final ProjectDeleteGateway projectDeleteGateway;
    private final Clock clock;

    public DeleteProjectHandler(
            CurrentActor currentActor,
            ProjectDeleteGateway projectDeleteGateway,
            Clock clock
    ) {
        this.currentActor = currentActor;
        this.projectDeleteGateway = projectDeleteGateway;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void deleteProject(DeleteProjectCommand command) {
        if (command == null || command.projectId() == null) {
            throw new InvalidProjectException("Project ID is required.");
        }
        if (command.expectedVersion() < 0) {
            throw new InvalidProjectException("If-Match must contain a non-negative project version.");
        }

        var actor = currentActor.getRequiredActor();
        var project = projectDeleteGateway.findActiveState(command.projectId())
                .orElseThrow(ProjectNotFoundException::new);
        if (!actor.id().equals(project.ownerId())) {
            throw new ProjectForbiddenException();
        }
        if (project.version() != command.expectedVersion()) {
            throw new ProjectStaleVersionException();
        }
        if ("PUBLISHED".equals(project.status())) {
            throw new ProjectNotDeletableException();
        }
        if (!projectDeleteGateway.softDeleteIfCurrent(
                command.projectId(), actor.id(), command.expectedVersion(), clock.instant())) {
            throw new ProjectStaleVersionException();
        }
    }
}
