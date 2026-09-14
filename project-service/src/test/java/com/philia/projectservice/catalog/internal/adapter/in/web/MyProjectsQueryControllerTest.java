package com.philia.projectservice.catalog.internal.adapter.in.web;

import com.philia.projectservice.catalog.api.ListMyProjectsQuery;
import com.philia.projectservice.catalog.api.ListMyProjectsUseCase;
import com.philia.projectservice.catalog.api.ProjectPageResult;
import com.philia.projectservice.catalog.api.ProjectSummaryResult;
import com.philia.projectservice.catalog.internal.adapter.in.web.mapper.ProjectSummaryWebMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MyProjectsQueryControllerTest {

    @Test
    void returnsTheStandardProjectCollectionResponse() {
        var result = new ProjectPageResult<>(List.of(project()), 0, 20, 1, 1);
        ListMyProjectsUseCase useCase = query -> result;
        var controller = new MyProjectsQueryController(useCase, new ProjectSummaryWebMapper());

        var response = controller.listMyProjects(null, null, null, 0, 20, "createdAt,desc");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().code()).isEqualTo("MY_PROJECTS_RETRIEVED");
        assertThat(response.getBody().data().items()).hasSize(1);
        assertThat(response.getBody().data().items().getFirst().title()).isEqualTo("Fiurozz Backend");
    }

    private static ProjectSummaryResult project() {
        var now = Instant.parse("2026-07-28T02:00:00Z");
        return new ProjectSummaryResult(
                UUID.randomUUID(), "Fiurozz Backend", "fiurozz-backend", "Short description", null,
                "DRAFT", "PRIVATE", null, now, now, 0
        );
    }
}
