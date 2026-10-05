package com.philia.projectservice.files.internal.adapter.in.web;

import com.philia.projectservice.files.api.*;
import com.philia.projectservice.files.internal.adapter.in.web.dto.request.*;
import com.philia.projectservice.files.internal.domain.FilePolicy;
import com.philia.projectservice.shared.web.ApiResponse;
import com.philia.projectservice.shared.openapi.OpenApiConfiguration;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

/** Gateway strips /api/projects; internal AI calls use these service-relative paths. */
@RestController
@RequestMapping("/{projectId}")
@Tag(name = "Project files", description = "Immutable code revisions and renewable edit leases")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
@ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Malformed request, unsafe path, invalid text or validation failure."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication is required."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Only the project owner may perform this operation."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Project, file, revision or lease is missing or inaccessible."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "PROJECT_INVALID_STATE: archived projects cannot be edited."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "412", description = "FILES_STALE_REVISION: read the current ETag before retrying.",
                headers = @Header(name = "ETag", schema = @Schema(type = "string"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413", description = "FILES_LIMIT_EXCEEDED."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "423", description = "PROJECT_LEASED: response data is lease status without its secret."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "FILES_STORAGE_UNAVAILABLE.")
})
public class ProjectFilesController {
    private final ProjectFilesUseCase useCase;
    public ProjectFilesController(ProjectFilesUseCase useCase) { this.useCase = useCase; }

    @GetMapping("/files")
    @Operation(summary = "Read project files", description = "Manifest by default; include=content adds UTF-8 contents. Non-owners only see the published source revision.")
    public ResponseEntity<ApiResponse<FilesResults.Tree>> files(@PathVariable UUID projectId,
            @RequestParam(required = false) Long revision, @RequestParam(required = false) String include) {
        if (include != null && !"content".equals(include)) FilePolicy.invalid("include must be content or omitted.");
        var result = useCase.getFiles(projectId, revision, "content".equals(include));
        return ResponseEntity.ok().eTag(quoted(result.revision()))
                .body(ApiResponse.success("FILES_RETRIEVED", "Project files retrieved.", result));
    }

    @PostMapping("/files/changes")
    @Operation(summary = "Atomically apply file changes", description = "Requires a quoted files If-Match; Lease-Id is required while leased. 412 returns the current ETag.")
    public ResponseEntity<ApiResponse<FilesResults.Saved>> changes(@PathVariable UUID projectId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestHeader(value = "Lease-Id", required = false) UUID leaseId,
            @RequestBody ApplyFileChangesRequest request) {
        var result = useCase.applyChanges(new ApplyFileChangesCommand(projectId, revision(ifMatch), leaseId,
                request.changes(), request.label(), request.source()));
        return ResponseEntity.ok().eTag(quoted(result.revision()))
                .body(ApiResponse.success("FILES_SAVED", "Project files saved.", result));
    }

    @GetMapping("/files/revisions")
    @Operation(summary = "List immutable file revisions", description = "Owner only, newest first. page starts at 0; size is 1..100.")
    public ApiResponse<FilesResults.Page> revisions(@PathVariable UUID projectId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success("FILE_REVISIONS_RETRIEVED", "File revisions retrieved.", useCase.listRevisions(projectId, page, size));
    }

    @PostMapping("/files/revert")
    @Operation(summary = "Restore a previous file tree as a new revision")
    public ResponseEntity<ApiResponse<FilesResults.Reverted>> revert(@PathVariable UUID projectId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestHeader(value = "Lease-Id", required = false) UUID leaseId, @RequestBody RevertFilesRequest request) {
        if (request.toRevision() == null) FilePolicy.invalid("toRevision is required.");
        var result = useCase.revert(new RevertFilesCommand(projectId, revision(ifMatch), leaseId, request.toRevision(), request.label()));
        return ResponseEntity.ok().eTag(quoted(result.revision()))
                .body(ApiResponse.success("FILES_REVERTED", "Project files reverted.", result));
    }

    @PostMapping("/lease")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    @Operation(summary = "Acquire an edit lease", description = "Returns the durable baseRevision. ttlSeconds is 30..3600; default 120.")
    public ResponseEntity<ApiResponse<FilesResults.Lease>> acquire(@PathVariable UUID projectId,
            @RequestBody AcquireLeaseRequest request) {
        var result = useCase.acquireLease(new AcquireLeaseCommand(projectId, request.holder(), request.runId(),
                request.ttlSeconds() == null ? 120 : request.ttlSeconds()));
        return ResponseEntity.status(201).body(ApiResponse.success("LEASE_ACQUIRED", "Edit lease acquired.", result));
    }

    @PutMapping("/lease/{leaseId}")
    @Operation(summary = "Renew an edit lease")
    public ApiResponse<FilesResults.Lease> renew(@PathVariable UUID projectId, @PathVariable UUID leaseId) {
        return ApiResponse.success("LEASE_RENEWED", "Edit lease renewed.", useCase.renewLease(projectId, leaseId));
    }

    @DeleteMapping("/lease/{leaseId}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    @Operation(summary = "Release an edit lease", description = "Unknown or expired lease IDs return 204.")
    public ResponseEntity<Void> release(@PathVariable UUID projectId, @PathVariable UUID leaseId) {
        useCase.releaseLease(projectId, leaseId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/lease")
    @Operation(summary = "Read edit lease status", description = "Returns data:null if unlocked; never exposes leaseId.")
    public ApiResponse<FilesResults.LeaseStatus> lease(@PathVariable UUID projectId) {
        return ApiResponse.success("LEASE_RETRIEVED", "Edit lease status retrieved.", useCase.getLease(projectId));
    }

    private static long revision(String ifMatch) {
        if (ifMatch == null || !ifMatch.matches("\\\"[0-9]+\\\"")) FilePolicy.invalid("If-Match must be a quoted files revision, for example \"0\".");
        try { return Long.parseLong(ifMatch.substring(1, ifMatch.length() - 1)); }
        catch (NumberFormatException exception) { FilePolicy.invalid("If-Match revision is too large."); return 0; }
    }
    private static String quoted(long revision) { return "\"" + revision + "\""; }
}
