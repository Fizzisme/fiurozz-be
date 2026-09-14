package com.philia.projectservice.catalog.internal.application.port.out;

import com.philia.projectservice.catalog.api.ProjectCursorResult;
import com.philia.projectservice.catalog.api.PublicProjectCardResult;
import com.philia.projectservice.catalog.api.PublicProjectSearchQuery;

public interface PublicProjectQuery {
    ProjectCursorResult<PublicProjectCardResult> search(PublicProjectSearchQuery query);
}
