package com.philia.projectservice.catalog.internal.application;

import com.philia.projectservice.catalog.api.ListMyProjectsQuery;
import com.philia.projectservice.catalog.api.ListMyProjectsUseCase;
import com.philia.projectservice.catalog.api.ProjectPageResult;
import com.philia.projectservice.catalog.api.ProjectSummaryResult;
import com.philia.projectservice.catalog.internal.application.port.out.CurrentActor;
import com.philia.projectservice.catalog.internal.application.port.out.ProjectSummaryQuery;
import com.philia.projectservice.catalog.internal.domain.exception.InvalidProjectException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the authenticated owner before querying their active projects.
 */
@Service
public class ListMyProjectsHandler implements ListMyProjectsUseCase {

    private final ProjectSummaryQuery projectSummaryQuery;
    private final CurrentActor currentActor;

    public ListMyProjectsHandler(ProjectSummaryQuery projectSummaryQuery, CurrentActor currentActor) {
        this.projectSummaryQuery = projectSummaryQuery;
        this.currentActor = currentActor;
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectPageResult<ProjectSummaryResult> listMyProjects(ListMyProjectsQuery query) {
        if (query == null) {
            throw new InvalidProjectException("Project list query is required.");
        }
        return projectSummaryQuery.findActiveByOwner(currentActor.getRequiredActor().id(), query);
    }
}
