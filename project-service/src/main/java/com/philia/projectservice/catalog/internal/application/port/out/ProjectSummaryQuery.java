package com.philia.projectservice.catalog.internal.application.port.out;

import com.philia.projectservice.catalog.api.ListMyProjectsQuery;
import com.philia.projectservice.catalog.api.ProjectPageResult;
import com.philia.projectservice.catalog.api.ProjectSummaryResult;

import java.util.UUID;

/**
 * Read port for a paginated list of one owner's active projects.
 */
public interface ProjectSummaryQuery {

    ProjectPageResult<ProjectSummaryResult> findActiveByOwner(
            UUID ownerId,
            ListMyProjectsQuery query
    );
}
