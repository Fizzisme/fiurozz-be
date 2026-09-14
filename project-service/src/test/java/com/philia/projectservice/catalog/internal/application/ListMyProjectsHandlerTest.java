package com.philia.projectservice.catalog.internal.application;

import com.philia.projectservice.catalog.api.ListMyProjectsQuery;
import com.philia.projectservice.catalog.api.ProjectListSort;
import com.philia.projectservice.catalog.api.ProjectPageResult;
import com.philia.projectservice.catalog.api.ProjectSummaryResult;
import com.philia.projectservice.catalog.internal.application.port.out.CurrentActor;
import com.philia.projectservice.catalog.internal.application.port.out.ProjectSummaryQuery;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ListMyProjectsHandlerTest {

    @Test
    void scopesTheQueryToTheAuthenticatedActor() {
        var actor = new CurrentActor.Actor(UUID.randomUUID(), "Owner", null);
        var expected = new ProjectPageResult<>(List.of(project()), 0, 20, 1, 1);
        var query = new ListMyProjectsQuery(null, null, null, 0, 20, ProjectListSort.CREATED_AT_DESC);
        var capturedOwnerId = new UUID[1];
        var capturedQuery = new ListMyProjectsQuery[1];
        ProjectSummaryQuery projectSummaryQuery = (ownerId, receivedQuery) -> {
            capturedOwnerId[0] = ownerId;
            capturedQuery[0] = receivedQuery;
            return expected;
        };
        CurrentActor currentActor = () -> Optional.of(actor);
        var handler = new ListMyProjectsHandler(projectSummaryQuery, currentActor);

        var result = handler.listMyProjects(query);

        assertThat(result).isEqualTo(expected);
        assertThat(capturedOwnerId[0]).isEqualTo(actor.id());
        assertThat(capturedQuery[0]).isSameAs(query);
    }

    private static ProjectSummaryResult project() {
        var now = Instant.parse("2026-07-28T02:00:00Z");
        return new ProjectSummaryResult(
                UUID.randomUUID(), "Fiurozz Backend", "fiurozz-backend", "Short description", null,
                "DRAFT", "PRIVATE", null, now, now, 0
        );
    }
}
