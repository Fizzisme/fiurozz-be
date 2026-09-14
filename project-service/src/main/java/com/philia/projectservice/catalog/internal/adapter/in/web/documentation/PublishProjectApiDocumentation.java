package com.philia.projectservice.catalog.internal.adapter.in.web.documentation;

import com.philia.projectservice.catalog.internal.adapter.in.web.dto.response.ProjectDetailResponse;
import com.philia.projectservice.shared.openapi.ApiErrorResponseDocumentation;
import com.philia.projectservice.shared.openapi.OpenApiConfiguration;
import com.philia.projectservice.shared.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
public interface PublishProjectApiDocumentation {

    @Operation(
            operationId = "publishProject",
            summary = "Publish a project",
            description = "Publishes an owned DRAFT project after validating its active subcategory and tags. "
                    + "Repeating a request with the current ETag for an already published project is idempotent."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Project published successfully.", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Authentication is required.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponseDocumentation.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "The caller does not own the project.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponseDocumentation.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "The project does not exist.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponseDocumentation.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "The project cannot be published in its current state.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponseDocumentation.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "412", description = "The supplied ETag is stale.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponseDocumentation.class)))
    })
    ResponseEntity<ApiResponse<ProjectDetailResponse>> publishProject(UUID projectId, String ifMatch);
}
