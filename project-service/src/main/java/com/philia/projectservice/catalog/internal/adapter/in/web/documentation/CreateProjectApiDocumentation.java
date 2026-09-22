package com.philia.projectservice.catalog.internal.adapter.in.web.documentation;

import com.philia.projectservice.catalog.internal.adapter.in.web.dto.request.CreateProjectRequest;
import com.philia.projectservice.catalog.internal.adapter.in.web.dto.response.ProjectDetailResponse;
import com.philia.projectservice.shared.openapi.ApiErrorResponseDocumentation;
import com.philia.projectservice.shared.openapi.OpenApiConfiguration;
import com.philia.projectservice.shared.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(
        name = "Project Catalog",
        description = "Create and manage projects owned by the authenticated user."
)
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
public interface CreateProjectApiDocumentation {

    @Operation(
            operationId = "createProject",
            summary = "Create a project",
            description = """
                    Creates a new draft project for the authenticated owner.

                    The service validates the subcategory and tags, generates the slug from the
                    title, normalizes the technology stack, and persists the project and its tags
                    in one transaction.
                    Owner information is obtained from authentication and cannot be supplied in
                    the request body.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Project created successfully.",
                    useReturnTypeSchema = true,
                    headers = {
                            @Header(
                                    name = "Location",
                                    description = "URI of the created project.",
                                    schema = @Schema(type = "string", format = "uri")
                            ),
                            @Header(
                                    name = "ETag",
                                    description = "Initial optimistic-lock version of the project.",
                                    schema = @Schema(type = "string", example = "\"0\"")
                            )
                    }
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "The request body or one of its fields is invalid.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponseDocumentation.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "success": false,
                                              "code": "VALIDATION_FAILED",
                                              "message": "One or more request fields are invalid.",
                                              "data": null,
                                              "errors": {"title": "title is required"},
                                              "timestamp": "2026-07-24T09:00:00Z"
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "A valid authenticated actor is required.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponseDocumentation.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "The slug already exists or a catalog reference is unavailable.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponseDocumentation.class)
                    )
            )
    })
    ResponseEntity<ApiResponse<ProjectDetailResponse>> createProject(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Project information controlled by the owner.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CreateProjectRequest.class),
                            examples = @ExampleObject(
                                    name = "Create a private draft project",
                                    value = """
                                            {
                                              "subCategoryId": "939dbfc5-e00c-40d8-9351-499df2562304",
                                              "title": "Fiurozz Backend",
                                              "shortDescription": "A platform for publishing software projects.",
                                              "description": "The complete project description.",
                                              "demoUrl": "https://demo.example.com",
                                              "visibility": "PRIVATE",
                                              "techStack": ["java", "spring-boot", "postgresql"],
                                              "features": ["Project catalog", "Project discovery"],
                                              "tagIds": ["2ed51a2d-3ca7-4463-8402-c82a12255c92"]
                                            }
                                            """
                            )
                    )
            )
            CreateProjectRequest request
    );

}
