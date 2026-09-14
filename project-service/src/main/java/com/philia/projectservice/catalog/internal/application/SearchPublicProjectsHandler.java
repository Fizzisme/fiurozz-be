package com.philia.projectservice.catalog.internal.application;

import com.philia.projectservice.catalog.api.ProjectCursorResult;
import com.philia.projectservice.catalog.api.PublicProjectCardResult;
import com.philia.projectservice.catalog.api.PublicProjectSearchQuery;
import com.philia.projectservice.catalog.api.SearchPublicProjectsUseCase;
import com.philia.projectservice.catalog.internal.application.port.out.PublicProjectQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SearchPublicProjectsHandler implements SearchPublicProjectsUseCase {
    private final PublicProjectQuery query;
    public SearchPublicProjectsHandler(PublicProjectQuery query) { this.query = query; }
    @Override @Transactional(readOnly = true)
    public ProjectCursorResult<PublicProjectCardResult> searchPublicProjects(PublicProjectSearchQuery request) {
        return query.search(request);
    }
}
