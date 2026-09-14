package com.philia.projectservice.catalog.internal.adapter.in.web;

import com.philia.projectservice.catalog.api.ListMyProjectsQuery;
import com.philia.projectservice.catalog.api.ListMyProjectsUseCase;
import com.philia.projectservice.catalog.api.ProjectListSort;
import com.philia.projectservice.catalog.internal.adapter.in.web.documentation.ListMyProjectsApiDocumentation;
import com.philia.projectservice.catalog.internal.adapter.in.web.dto.response.ProjectPageResponse;
import com.philia.projectservice.catalog.internal.adapter.in.web.mapper.ProjectSummaryWebMapper;
import com.philia.projectservice.catalog.internal.domain.ProjectStatus;
import com.philia.projectservice.catalog.internal.domain.ProjectVisibility;
import com.philia.projectservice.shared.web.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP input adapter for a signed-in owner's project dashboard.
 */
@RestController
@RequestMapping("/v1/me/projects")
public final class MyProjectsQueryController implements ListMyProjectsApiDocumentation {

    private final ListMyProjectsUseCase listMyProjectsUseCase;
    private final ProjectSummaryWebMapper projectSummaryWebMapper;

    public MyProjectsQueryController(
            ListMyProjectsUseCase listMyProjectsUseCase,
            ProjectSummaryWebMapper projectSummaryWebMapper
    ) {
        this.listMyProjectsUseCase = listMyProjectsUseCase;
        this.projectSummaryWebMapper = projectSummaryWebMapper;
    }

    @Override
    @GetMapping
    public ResponseEntity<ApiResponse<ProjectPageResponse>> listMyProjects(
            @RequestParam(required = false) ProjectStatus status,
            @RequestParam(required = false) ProjectVisibility visibility,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        var result = listMyProjectsUseCase.listMyProjects(new ListMyProjectsQuery(
                status,
                visibility,
                query,
                page,
                size,
                ProjectListSort.fromRequest(sort)
        ));

        return ResponseEntity.ok(ApiResponse.success(
                "MY_PROJECTS_RETRIEVED",
                "Projects retrieved successfully.",
                projectSummaryWebMapper.toPageResponse(result)
        ));
    }
}
