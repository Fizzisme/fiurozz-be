package com.philia.projectservice.catalog.api;

public interface SearchPublicProjectsUseCase {
    ProjectCursorResult<PublicProjectCardResult> searchPublicProjects(PublicProjectSearchQuery query);
}
