package com.philia.projectservice.catalog.api;

/**
 * Lists the active projects owned by the authenticated actor.
 */
public interface ListMyProjectsUseCase {

    ProjectPageResult<ProjectSummaryResult> listMyProjects(ListMyProjectsQuery query);
}
