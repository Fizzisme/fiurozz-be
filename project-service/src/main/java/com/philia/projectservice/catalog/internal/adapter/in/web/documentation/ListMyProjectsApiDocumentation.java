package com.philia.projectservice.catalog.internal.adapter.in.web.documentation;

import com.philia.projectservice.catalog.internal.domain.ProjectStatus;
import com.philia.projectservice.catalog.internal.domain.ProjectVisibility;
import com.philia.projectservice.catalog.internal.adapter.in.web.dto.response.ProjectPageResponse;
import com.philia.projectservice.shared.openapi.ApiErrorResponseDocumentation;
import com.philia.projectservice.shared.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "Project Catalog", description = "Create and manage projects in the catalog.")
public interface ListMyProjectsApiDocumentation {

    @Operation(
            operationId = "listMyProjects",
            summary = "List my projects",
            description = """
                    Returns a zero-based page of active projects owned by the authenticated actor.
                    Soft-deleted projects are always excluded. Results may be filtered by status,
                    visibility, and a case-insensitive title or description query.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Projects retrieved successfully.",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "A filter, page, size, or sort parameter is invalid.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponseDocumentation.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponseDocumentation.class))
            )
    })
    ResponseEntity<ApiResponse<ProjectPageResponse>> listMyProjects(
            @Parameter(description = "Optional project status.", example = "DRAFT") ProjectStatus status,
            @Parameter(description = "Optional project visibility.", example = "PRIVATE") ProjectVisibility visibility,
            @Parameter(description = "Case-insensitive title or description search query.", example = "backend") String query,
            @Parameter(description = "Zero-based page number.", example = "0") int page,
            @Parameter(description = "Page size from 1 to 50.", example = "20") int size,
            @Parameter(description = "Allowed values: createdAt,desc; createdAt,asc; updatedAt,desc.",
                    example = "createdAt,desc") String sort
    );
}
