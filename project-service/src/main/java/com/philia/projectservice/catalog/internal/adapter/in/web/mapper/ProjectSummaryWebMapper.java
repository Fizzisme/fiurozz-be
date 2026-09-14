package com.philia.projectservice.catalog.internal.adapter.in.web.mapper;

import com.philia.projectservice.catalog.api.ProjectPageResult;
import com.philia.projectservice.catalog.api.ProjectSummaryResult;
import com.philia.projectservice.catalog.internal.adapter.in.web.dto.response.ProjectPageResponse;
import com.philia.projectservice.catalog.internal.adapter.in.web.dto.response.ProjectSummaryResponse;
import org.springframework.stereotype.Component;

@Component
public class ProjectSummaryWebMapper {

    public ProjectPageResponse toPageResponse(ProjectPageResult<ProjectSummaryResult> result) {
        return new ProjectPageResponse(
                result.items().stream().map(this::toSummaryResponse).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages()
        );
    }

    private ProjectSummaryResponse toSummaryResponse(ProjectSummaryResult result) {
        return new ProjectSummaryResponse(
                result.id(),
                result.title(),
                result.slug(),
                result.shortDescription(),
                result.thumbnailUrl(),
                result.status(),
                result.visibility(),
                result.publishedAt(),
                result.createdAt(),
                result.updatedAt(),
                result.version()
        );
    }
}
