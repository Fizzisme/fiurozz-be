package com.philia.projectservice.catalog.internal.application.port.out;

import com.philia.projectservice.catalog.api.ProjectPageResult;
import com.philia.projectservice.catalog.api.ProjectSummaryResult;
import com.philia.projectservice.catalog.api.PublicProjectSearchQuery;

public interface PublicProjectQuery {
    ProjectPageResult<ProjectSummaryResult> search(PublicProjectSearchQuery query);
}
