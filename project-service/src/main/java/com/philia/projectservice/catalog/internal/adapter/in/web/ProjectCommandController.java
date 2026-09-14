package com.philia.projectservice.catalog.internal.adapter.in.web;

import com.philia.projectservice.catalog.api.CreateProjectUseCase;
import com.philia.projectservice.catalog.api.DeleteProjectCommand;
import com.philia.projectservice.catalog.api.DeleteProjectUseCase;
import com.philia.projectservice.catalog.api.ReplaceProjectTagsUseCase;
import com.philia.projectservice.catalog.api.UpdateProjectUseCase;
import com.philia.projectservice.catalog.api.PublishProjectCommand;
import com.philia.projectservice.catalog.api.PublishProjectUseCase;
import com.philia.projectservice.catalog.internal.adapter.in.web.documentation.CreateProjectApiDocumentation;
import com.philia.projectservice.catalog.internal.adapter.in.web.documentation.DeleteProjectApiDocumentation;
import com.philia.projectservice.catalog.internal.adapter.in.web.documentation.ReplaceProjectTagsApiDocumentation;
import com.philia.projectservice.catalog.internal.adapter.in.web.documentation.UpdateProjectApiDocumentation;
import com.philia.projectservice.catalog.internal.adapter.in.web.documentation.PublishProjectApiDocumentation;
import com.philia.projectservice.catalog.internal.adapter.in.web.dto.request.CreateProjectRequest;
import com.philia.projectservice.catalog.internal.adapter.in.web.dto.request.ReplaceProjectTagsRequest;
import com.philia.projectservice.catalog.internal.adapter.in.web.dto.request.UpdateProjectRequest;
import com.philia.projectservice.catalog.internal.adapter.in.web.dto.response.ProjectDetailResponse;
import com.philia.projectservice.catalog.internal.adapter.in.web.dto.response.ProjectTagsResponse;
import com.philia.projectservice.catalog.internal.adapter.in.web.mapper.CreateProjectWebMapper;
import com.philia.projectservice.catalog.internal.adapter.in.web.mapper.ProjectDetailWebMapper;
import com.philia.projectservice.catalog.internal.adapter.in.web.mapper.ProjectTagsWebMapper;
import com.philia.projectservice.catalog.internal.adapter.in.web.mapper.UpdateProjectWebMapper;
import com.philia.projectservice.catalog.internal.domain.exception.InvalidProjectException;
import com.philia.projectservice.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/v1/projects")
public final class ProjectCommandController implements CreateProjectApiDocumentation, ReplaceProjectTagsApiDocumentation,
        UpdateProjectApiDocumentation, DeleteProjectApiDocumentation, PublishProjectApiDocumentation {

    private final CreateProjectUseCase createProjectUseCase;
    private final CreateProjectWebMapper createProjectWebMapper;
    private final ProjectDetailWebMapper projectDetailWebMapper;
    private final ReplaceProjectTagsUseCase replaceProjectTagsUseCase;
    private final ProjectTagsWebMapper projectTagsWebMapper;
    private final UpdateProjectUseCase updateProjectUseCase;
    private final UpdateProjectWebMapper updateProjectWebMapper;
    private final DeleteProjectUseCase deleteProjectUseCase;
    private final PublishProjectUseCase publishProjectUseCase;

    public ProjectCommandController(
            CreateProjectUseCase createProjectUseCase,
            CreateProjectWebMapper createProjectWebMapper,
            ProjectDetailWebMapper projectDetailWebMapper,
            ReplaceProjectTagsUseCase replaceProjectTagsUseCase,
            ProjectTagsWebMapper projectTagsWebMapper,
            UpdateProjectUseCase updateProjectUseCase,
            UpdateProjectWebMapper updateProjectWebMapper,
            DeleteProjectUseCase deleteProjectUseCase,
            PublishProjectUseCase publishProjectUseCase
    ) {
        this.createProjectUseCase = createProjectUseCase;
        this.createProjectWebMapper = createProjectWebMapper;
        this.projectDetailWebMapper = projectDetailWebMapper;
        this.replaceProjectTagsUseCase = replaceProjectTagsUseCase;
        this.projectTagsWebMapper = projectTagsWebMapper;
        this.updateProjectUseCase = updateProjectUseCase;
        this.updateProjectWebMapper = updateProjectWebMapper;
        this.deleteProjectUseCase = deleteProjectUseCase;
        this.publishProjectUseCase = publishProjectUseCase;
    }

    @DeleteMapping("/{projectId}")
    @Override
    public ResponseEntity<Void> deleteProject(
            @PathVariable UUID projectId,
            @RequestHeader("If-Match") String ifMatch
    ) {
        deleteProjectUseCase.deleteProject(new DeleteProjectCommand(projectId, parseEtag(ifMatch)));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{projectId}/publish")
    @Override
    public ResponseEntity<ApiResponse<ProjectDetailResponse>> publishProject(
            @PathVariable UUID projectId,
            @RequestHeader("If-Match") String ifMatch
    ) {
        var result = publishProjectUseCase.publishProject(new PublishProjectCommand(projectId, parseEtag(ifMatch)));
        var response = projectDetailWebMapper.toResponse(result);

        return ResponseEntity.ok()
                .eTag('"' + Long.toString(response.version()) + '"')
                .body(ApiResponse.success(
                        "PROJECT_PUBLISHED",
                        "Project published successfully.",
                        response
                ));
    }

    @PatchMapping("/{projectId}")
    @Override
    public ResponseEntity<ApiResponse<ProjectDetailResponse>> updateProject(
            @PathVariable UUID projectId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody UpdateProjectRequest request
    ) {
        var command = updateProjectWebMapper.toCommand(projectId, parseEtag(ifMatch), request);
        var result = updateProjectUseCase.updateProject(command);
        var response = projectDetailWebMapper.toResponse(result);

        return ResponseEntity.ok()
                .eTag('"' + Long.toString(response.version()) + '"')
                .body(ApiResponse.success(
                        "PROJECT_UPDATED",
                        "Project updated successfully.",
                        response
                ));
    }

    @PostMapping
    @Override
    public ResponseEntity<ApiResponse<ProjectDetailResponse>> createProject(
            @Valid @RequestBody CreateProjectRequest request
    ) {
        var command = createProjectWebMapper.toCommand(request);
        var result = createProjectUseCase.create(command);
        var response = projectDetailWebMapper.toResponse(result);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{projectId}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(location)
                .eTag('"' + Long.toString(response.version()) + '"')
                .body(ApiResponse.success(
                        "PROJECT_CREATED",
                        "Project created successfully.",
                        response
                ));
    }

    @PutMapping("/{projectId}/tags")
    @Override
    public ResponseEntity<ApiResponse<ProjectTagsResponse>> replaceProjectTags(
            @PathVariable UUID projectId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody ReplaceProjectTagsRequest request
    ) {
        // The ETag is supplied as a quoted HTTP header, for example: If-Match: "0".
        var command = projectTagsWebMapper.toCommand(projectId, parseEtag(ifMatch), request);
        var result = replaceProjectTagsUseCase.replaceProjectTags(command);
        var response = projectTagsWebMapper.toResponse(result);

        return ResponseEntity.ok()
                .eTag('"' + Long.toString(response.version()) + '"')
                .body(ApiResponse.success(
                        "PROJECT_TAGS_REPLACED",
                        "Project tags replaced successfully.",
                        response
                ));
    }

    private static long parseEtag(String ifMatch) {
        // Only a quoted numeric ETag is accepted for project mutations.
        if (ifMatch == null || ifMatch.length() < 3 || ifMatch.charAt(0) != '"'
                || ifMatch.charAt(ifMatch.length() - 1) != '"') {
            throw new InvalidProjectException("If-Match must contain a quoted project version, for example \"0\".");
        }
        try {
            return Long.parseLong(ifMatch.substring(1, ifMatch.length() - 1));
        } catch (NumberFormatException exception) {
            throw new InvalidProjectException("If-Match must contain a quoted project version, for example \"0\".");
        }
    }
}
