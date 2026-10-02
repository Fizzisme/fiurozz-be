package com.philia.projectservice.catalog.internal.adapter.in.web.mapper;

import com.philia.projectservice.catalog.api.ProjectDetailResult;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectDetailWebMapperTest {

    private final ProjectDetailWebMapper mapper = Mappers.getMapper(ProjectDetailWebMapper.class);

    @Test
    void mapsApplicationResultToResponseDto() {
        var now = Instant.parse("2026-07-24T02:00:00Z");
        var result = new ProjectDetailResult(
                UUID.randomUUID(),
                new ProjectDetailResult.Owner(UUID.randomUUID(), "Philia", null),
                new ProjectDetailResult.Category(UUID.randomUUID(), "software", "software", "Software", "code"),
                new ProjectDetailResult.SubCategory(UUID.randomUUID(), "backend", "backend", "Backend"),
                "Fiurozz Backend",
                "fiurozz-backend",
                "Short description",
                "Full description",
                null,
                "https://demo.example.com",
                List.of("java"),
                List.of("Project catalog"),
                List.of(new ProjectDetailResult.Tag(UUID.randomUUID(), "backend", "Backend")),
                "DRAFT",
                "PRIVATE",
                "HIDDEN",
                new ProjectDetailResult.Statistics(0, 0, 0),
                null,
                now,
                now,
                0
        );

        var response = mapper.toResponse(result);

        assertThat(response.id()).isEqualTo(result.id());
        assertThat(response.owner().displayName()).isEqualTo("Philia");
        assertThat(response.tags()).singleElement().satisfies(tag ->
                assertThat(tag.displayName()).isEqualTo("Backend")
        );
        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.version()).isZero();
        assertThat(response.media()).isEmpty();
    }

    @Test
    void mapsMediaToResponseDto() {
        var mediaId = UUID.randomUUID();
        var now = Instant.parse("2026-07-24T02:00:00Z");
        var result = new ProjectDetailResult(
                UUID.randomUUID(),
                new ProjectDetailResult.Owner(UUID.randomUUID(), "Philia", null),
                new ProjectDetailResult.Category(UUID.randomUUID(), "software", "software", "Software", "code"),
                new ProjectDetailResult.SubCategory(UUID.randomUUID(), "backend", "backend", "Backend"),
                "Fiurozz Backend", "fiurozz-backend", "Short description", "Full description",
                "https://cdn.example.com/a.png",
                List.of("https://cdn.example.com/a.png"),
                List.of(new ProjectDetailResult.Media(mediaId, "VIDEO", "https://cdn.example.com/demo.mp4", 3)),
                null, null, List.of(), List.of(), List.of(), "DRAFT", "PRIVATE", "HIDDEN",
                new ProjectDetailResult.Statistics(0, 0, 0), null, now, now, 0
        );

        var response = mapper.toResponse(result);

        assertThat(response.media()).singleElement().satisfies(media -> {
            assertThat(media.id()).isEqualTo(mediaId);
            assertThat(media.mediaType()).isEqualTo("VIDEO");
            assertThat(media.url()).isEqualTo("https://cdn.example.com/demo.mp4");
            assertThat(media.sortOrder()).isEqualTo(3);
        });
    }
}
