package com.philia.projectservice.catalog.internal.adapter.in.web;

import com.philia.projectservice.catalog.api.ArchiveProjectCommand;
import com.philia.projectservice.catalog.api.ArchiveProjectUseCase;
import com.philia.projectservice.catalog.api.ChangeProjectVisibilityCommand;
import com.philia.projectservice.catalog.api.ChangeProjectVisibilityUseCase;
import com.philia.projectservice.catalog.api.ProjectDetailResult;
import com.philia.projectservice.catalog.api.ReopenProjectCommand;
import com.philia.projectservice.catalog.api.ReopenProjectUseCase;
import com.philia.projectservice.catalog.internal.adapter.in.web.dto.request.ChangeProjectVisibilityRequest;
import com.philia.projectservice.catalog.internal.adapter.in.web.dto.response.ProjectDetailResponse;
import com.philia.projectservice.catalog.internal.adapter.in.web.mapper.ProjectDetailWebMapper;
import com.philia.projectservice.catalog.internal.domain.exception.InvalidProjectException;
import com.philia.projectservice.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/projects")
public class ProjectLifecycleController {
    private final ArchiveProjectUseCase archiveProjectUseCase;
    private final ReopenProjectUseCase reopenProjectUseCase;
    private final ChangeProjectVisibilityUseCase visibilityUseCase;
    private final ProjectDetailWebMapper mapper;

    public ProjectLifecycleController(ArchiveProjectUseCase archiveProjectUseCase, ReopenProjectUseCase reopenProjectUseCase,
                                      ChangeProjectVisibilityUseCase visibilityUseCase, ProjectDetailWebMapper mapper) {
        this.archiveProjectUseCase = archiveProjectUseCase;
        this.reopenProjectUseCase = reopenProjectUseCase;
        this.visibilityUseCase = visibilityUseCase;
        this.mapper = mapper;
    }

    @PostMapping("/{projectId}/archive")
    public ResponseEntity<ApiResponse<ProjectDetailResponse>> archiveProject(
            @PathVariable UUID projectId, @RequestHeader("If-Match") String ifMatch) {
        return response(archiveProjectUseCase.archiveProject(new ArchiveProjectCommand(projectId, parseEtag(ifMatch))),
                "PROJECT_ARCHIVED", "Project archived successfully.");
    }

    @PostMapping("/{projectId}/reopen")
    public ResponseEntity<ApiResponse<ProjectDetailResponse>> reopenProject(
            @PathVariable UUID projectId, @RequestHeader("If-Match") String ifMatch) {
        return response(reopenProjectUseCase.reopenProject(new ReopenProjectCommand(projectId, parseEtag(ifMatch))),
                "PROJECT_REOPENED", "Project reopened successfully.");
    }

    @PatchMapping("/{projectId}/visibility")
    public ResponseEntity<ApiResponse<ProjectDetailResponse>> changeVisibility(
            @PathVariable UUID projectId, @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody ChangeProjectVisibilityRequest request) {
        return response(visibilityUseCase.changeVisibility(new ChangeProjectVisibilityCommand(
                projectId, parseEtag(ifMatch), request.visibility())), "PROJECT_VISIBILITY_UPDATED",
                "Project visibility updated successfully.");
    }

    private ResponseEntity<ApiResponse<ProjectDetailResponse>> response(ProjectDetailResult result, String code, String message) {
        var response = mapper.toResponse(result);
        return ResponseEntity.ok().eTag('"' + Long.toString(response.version()) + '"')
                .body(ApiResponse.success(code, message, response));
    }

    private static long parseEtag(String ifMatch) {
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
