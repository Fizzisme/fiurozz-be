package com.philia.projectservice.catalog.internal.adapter.in.web;

import com.philia.projectservice.catalog.api.CreateProjectUseCase;
import com.philia.projectservice.catalog.api.DeleteProjectUseCase;
import com.philia.projectservice.catalog.api.ProjectDetailResult;
import com.philia.projectservice.catalog.api.ReplaceProjectTagsResult;
import com.philia.projectservice.catalog.api.ReplaceProjectTagsUseCase;
import com.philia.projectservice.catalog.api.UpdateProjectUseCase;
import com.philia.projectservice.catalog.api.PublishProjectUseCase;
import com.philia.projectservice.catalog.internal.adapter.in.web.dto.request.CreateProjectRequest;
import com.philia.projectservice.catalog.internal.adapter.in.web.mapper.CreateProjectWebMapper;
import com.philia.projectservice.catalog.internal.adapter.in.web.mapper.ProjectDetailWebMapper;
import com.philia.projectservice.catalog.internal.adapter.in.web.mapper.ProjectTagsWebMapper;
import com.philia.projectservice.catalog.internal.adapter.in.web.mapper.UpdateProjectWebMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectCommandControllerTest {

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void returnsCreatedApiResponseWithLocationAndEtag() {
        var result = result();
        CreateProjectUseCase useCase = command -> result;
        CreateProjectWebMapper createMapper = Mappers.getMapper(CreateProjectWebMapper.class);
        ProjectDetailWebMapper detailMapper = Mappers.getMapper(ProjectDetailWebMapper.class);
        ReplaceProjectTagsUseCase replaceTagsUseCase = command -> new ReplaceProjectTagsResult(
                command.projectId(), List.of(), command.expectedVersion() + 1);
        ProjectTagsWebMapper tagsMapper = Mappers.getMapper(ProjectTagsWebMapper.class);
        UpdateProjectUseCase updateProjectUseCase = command -> result;
        UpdateProjectWebMapper updateMapper = Mappers.getMapper(UpdateProjectWebMapper.class);
        DeleteProjectUseCase deleteProjectUseCase = command -> { };
        PublishProjectUseCase publishProjectUseCase = command -> result;
        var controller = new ProjectCommandController(
                useCase, createMapper, detailMapper, replaceTagsUseCase, tagsMapper, updateProjectUseCase, updateMapper,
                deleteProjectUseCase, publishProjectUseCase);
        var servletRequest = new MockHttpServletRequest("POST", "/v1/projects");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));

        var response = controller.createProject(new CreateProjectRequest(
                result.subCategory().id(),
                result.title(),
                result.slug(),
                result.shortDescription(),
                result.description(),
                result.demoUrl(),
                result.visibility(),
                result.techStack(),
                result.features(),
                result.tags().stream().map(ProjectDetailResult.Tag::id).toList()
        ));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        assertThat(response.getHeaders().getLocation().getPath())
                .isEqualTo("/v1/projects/" + result.id());
        assertThat(response.getHeaders().getETag()).isEqualTo("\"0\"");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().code()).isEqualTo("PROJECT_CREATED");
        assertThat(response.getBody().data().id()).isEqualTo(result.id());
    }

    @Test
    void publishesProjectWithTheNextEtag() {
        var result = result();
        PublishProjectUseCase publishProjectUseCase = command -> new ProjectDetailResult(
                result.id(), result.owner(), result.category(), result.subCategory(), result.title(), result.slug(),
                result.shortDescription(), result.description(), result.thumbnailUrl(), result.demoUrl(),
                result.techStack(), result.features(), result.tags(), "PUBLISHED", result.visibility(),
                result.sourceVisibility(), result.statistics(), Instant.parse("2026-07-28T03:00:00Z"),
                result.createdAt(), Instant.parse("2026-07-28T03:00:00Z"), 1
        );
        var controller = new ProjectCommandController(
                command -> result,
                Mappers.getMapper(CreateProjectWebMapper.class),
                Mappers.getMapper(ProjectDetailWebMapper.class),
                command -> new ReplaceProjectTagsResult(command.projectId(), List.of(), command.expectedVersion() + 1),
                Mappers.getMapper(ProjectTagsWebMapper.class),
                command -> result,
                Mappers.getMapper(UpdateProjectWebMapper.class),
                command -> { },
                publishProjectUseCase
        );

        var response = controller.publishProject(result.id(), "\"0\"");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getETag()).isEqualTo("\"1\"");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("PROJECT_PUBLISHED");
        assertThat(response.getBody().data().status()).isEqualTo("PUBLISHED");
    }

    private static ProjectDetailResult result() {
        var now = Instant.parse("2026-07-24T02:00:00Z");
        return new ProjectDetailResult(
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
    }
}
