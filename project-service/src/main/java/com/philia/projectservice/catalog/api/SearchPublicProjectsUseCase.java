package com.philia.projectservice.catalog.api;

public interface SearchPublicProjectsUseCase {
    ProjectPageResult<ProjectSummaryResult> searchPublicProjects(PublicProjectSearchQuery query);
}
