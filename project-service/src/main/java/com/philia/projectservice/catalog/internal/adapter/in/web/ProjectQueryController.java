package com.philia.projectservice.catalog.internal.adapter.in.web;

import com.philia.projectservice.catalog.api.GetProjectByIdUseCase;
import com.philia.projectservice.catalog.internal.adapter.in.web.documentation.GetProjectByIdApiDocumentation;
import com.philia.projectservice.catalog.internal.adapter.in.web.dto.response.ProjectDetailResponse;
import com.philia.projectservice.catalog.internal.adapter.in.web.mapper.ProjectDetailWebMapper;
import com.philia.projectservice.shared.web.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * HTTP input adapter for project catalog queries. Swagger descriptions live in the
 * implemented documentation interface to keep this controller focused on orchestration.
 */
@RestController
@RequestMapping("/v1/projects")
public final class ProjectQueryController implements GetProjectByIdApiDocumentation {

    private final GetProjectByIdUseCase getProjectByIdUseCase;
    private final ProjectDetailWebMapper projectDetailWebMapper;

    public ProjectQueryController(
            GetProjectByIdUseCase getProjectByIdUseCase,
            ProjectDetailWebMapper projectDetailWebMapper
    ) {
        this.getProjectByIdUseCase = getProjectByIdUseCase;
        this.projectDetailWebMapper = projectDetailWebMapper;
    }

    @Override
    @GetMapping("/{projectId}")
    public ResponseEntity<ApiResponse<ProjectDetailResponse>> getProjectById(
            @PathVariable UUID projectId
    ) {
        var result = getProjectByIdUseCase.getProject(projectId);
        var response = projectDetailWebMapper.toResponse(result);

        return ResponseEntity.ok()
                .eTag('"' + Long.toString(response.version()) + '"')
                .body(ApiResponse.success(
                        "PROJECT_RETRIEVED",
                        "Project retrieved successfully.",
                        response
                ));
    }
}
