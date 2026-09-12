package com.philia.projectservice.catalog.internal.adapter.in.web;

import com.philia.projectservice.catalog.api.PublicProjectSearchQuery;
import com.philia.projectservice.catalog.api.SearchPublicProjectsUseCase;
import com.philia.projectservice.catalog.internal.adapter.in.web.dto.response.ProjectDetailResponse;
import com.philia.projectservice.catalog.internal.adapter.in.web.dto.response.ProjectPageResponse;
import com.philia.projectservice.catalog.internal.adapter.in.web.mapper.ProjectDetailWebMapper;
import com.philia.projectservice.catalog.internal.adapter.in.web.mapper.ProjectSummaryWebMapper;
import com.philia.projectservice.catalog.internal.application.exception.ProjectNotFoundException;
import com.philia.projectservice.catalog.internal.application.port.out.ProjectDetailQuery;
import com.philia.projectservice.catalog.internal.domain.exception.InvalidProjectException;
import com.philia.projectservice.shared.web.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.UUID;

@RestController
public class PublicProjectQueryController {
    private final SearchPublicProjectsUseCase search;
    private final ProjectSummaryWebMapper summaryMapper;
    private final ProjectDetailQuery detailQuery;
    private final ProjectDetailWebMapper detailMapper;

    public PublicProjectQueryController(SearchPublicProjectsUseCase search, ProjectSummaryWebMapper summaryMapper,
                                        ProjectDetailQuery detailQuery, ProjectDetailWebMapper detailMapper) {
        this.search = search;
        this.summaryMapper = summaryMapper;
        this.detailQuery = detailQuery;
        this.detailMapper = detailMapper;
    }

    @GetMapping("/v1/projects")
    public ResponseEntity<ApiResponse<ProjectPageResponse>> searchProjects(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(required = false) UUID categoryId, @RequestParam(required = false) UUID subCategoryId,
            @RequestParam(required = false) String tag, @RequestParam(required = false) UUID ownerId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "newest") String sort) {
        var result = search.searchPublicProjects(new PublicProjectSearchQuery(query, categoryId, subCategoryId, tag,
                ownerId, page, size, parseSort(sort)));
        return ResponseEntity.ok(ApiResponse.success("PUBLIC_PROJECTS_RETRIEVED", "Public projects retrieved successfully.",
                summaryMapper.toPageResponse(result)));
    }

    @GetMapping("/v1/owners/{ownerId}/projects/{slug}")
    public ResponseEntity<ApiResponse<ProjectDetailResponse>> getPublicProjectBySlug(
            @PathVariable UUID ownerId, @PathVariable String slug) {
        var result = detailQuery.findPublicByOwnerAndSlug(ownerId, slug).orElseThrow(ProjectNotFoundException::new);
        var response = detailMapper.toResponse(result);
        return ResponseEntity.ok().eTag('"' + Long.toString(response.version()) + '"')
                .body(ApiResponse.success("PUBLIC_PROJECT_RETRIEVED", "Public project retrieved successfully.", response));
    }

    private static PublicProjectSearchQuery.SortOrder parseSort(String value) {
        try { return PublicProjectSearchQuery.SortOrder.valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (RuntimeException exception) { throw new InvalidProjectException("Sort must be newest or oldest."); }
    }
}
