package com.philia.projectservice.catalog.internal.application;

import com.philia.projectservice.catalog.api.ChangeProjectVisibilityCommand;
import com.philia.projectservice.catalog.api.ChangeProjectVisibilityUseCase;
import com.philia.projectservice.catalog.api.ProjectDetailResult;
import com.philia.projectservice.catalog.internal.application.exception.ProjectForbiddenException;
import com.philia.projectservice.catalog.internal.application.exception.ProjectNotFoundException;
import com.philia.projectservice.catalog.internal.application.exception.ProjectStaleVersionException;
import com.philia.projectservice.catalog.internal.application.port.out.CurrentActor;
import com.philia.projectservice.catalog.internal.application.port.out.ProjectDetailQuery;
import com.philia.projectservice.catalog.internal.application.port.out.ProjectLifecycleGateway;
import com.philia.projectservice.catalog.internal.domain.exception.InvalidProjectException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class ChangeProjectVisibilityHandler implements ChangeProjectVisibilityUseCase {
    private final CurrentActor currentActor;
    private final ProjectLifecycleGateway lifecycle;
    private final ProjectDetailQuery details;
    private final Clock clock;

    public ChangeProjectVisibilityHandler(CurrentActor currentActor, ProjectLifecycleGateway lifecycle,
                                          ProjectDetailQuery details, Clock clock) {
        this.currentActor = currentActor;
        this.lifecycle = lifecycle;
        this.details = details;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ProjectDetailResult changeVisibility(ChangeProjectVisibilityCommand command) {
        if (command == null || command.projectId() == null || command.visibility() == null || command.expectedVersion() < 0) {
            throw new InvalidProjectException("Project ID, visibility, and a non-negative If-Match version are required.");
        }
        var actor = currentActor.getRequiredActor();
        var project = lifecycle.findActiveState(command.projectId()).orElseThrow(ProjectNotFoundException::new);
        if (!actor.id().equals(project.ownerId())) throw new ProjectForbiddenException();
        if (project.version() != command.expectedVersion()) throw new ProjectStaleVersionException();
        if (!lifecycle.changeVisibilityIfCurrent(command.projectId(), actor.id(), command.expectedVersion(),
                command.visibility(), clock.instant())) throw new ProjectStaleVersionException();
        return details.findActiveById(command.projectId()).orElseThrow(ProjectNotFoundException::new);
    }
}
