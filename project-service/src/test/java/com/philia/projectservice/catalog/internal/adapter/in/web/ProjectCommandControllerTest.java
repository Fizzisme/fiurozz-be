package com.philia.projectservice.catalog.internal.adapter.in.web;

import com.philia.projectservice.catalog.api.CreateProjectCommand;
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
import com.philia.projectservice.shared.web.ApiExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProjectCommandControllerTest {

    private static final UUID TAG_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};
    private static final byte[] MP4 = {0, 0, 0, 0x20, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
    private static final String PROJECT_JSON = """
            {
              "subCategoryId": "33333333-3333-3333-3333-333333333333",
              "title": "Fiurozz Backend",
              "shortDescription": "A project catalog backend",
              "description": "The complete project description",
              "tagIds": ["44444444-4444-4444-4444-444444444444"]
            }
            """;

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
        var servletRequest = new MockHttpServletRequest("POST", "/");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));

        var response = controller.createProject(new CreateProjectRequest(
                result.subCategory().id(),
                result.title(),
                result.shortDescription(),
                result.description(),
                result.demoUrl(),
                result.githubUrl(),
                result.visibility(),
                result.techStack(),
                result.features(),
                result.tags().stream().map(ProjectDetailResult.Tag::id).toList()
        ), List.of(png("images")), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        assertThat(response.getHeaders().getLocation().getPath())
                .isEqualTo("/" + result.id());
        assertThat(response.getHeaders().getETag()).isEqualTo("\"0\"");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().code()).isEqualTo("PROJECT_CREATED");
        assertThat(response.getBody().data().id()).isEqualTo(result.id());
    }

    @Test
    void bindsMultipartPartsIntoTheCreateCommand() throws Exception {
        var captured = new AtomicReference<CreateProjectCommand>();
        var result = result();
        var mockMvc = mockMvc(controller(command -> {
            captured.set(command);
            return result;
        }));

        mockMvc.perform(multipart("/")
                        .file(projectPart(MediaType.APPLICATION_JSON_VALUE))
                        .file(png("images"))
                        .file(png("images"))
                        .file(png("images"))
                        .file(new MockMultipartFile("video", "demo.mp4", "video/mp4", MP4)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("PROJECT_CREATED"));

        var command = captured.get();
        assertThat(command.title()).isEqualTo("Fiurozz Backend");
        assertThat(command.tagIds()).containsExactly(TAG_ID);
        assertThat(command.images()).hasSize(3).allSatisfy(image -> {
            assertThat(image.filename()).isEqualTo("image.png");
            assertThat(image.sizeBytes()).isEqualTo(PNG.length);
            try (var content = image.content().open()) {
                assertThat(content.readAllBytes()).isEqualTo(PNG);
            }
        });
        assertThat(command.video().filename()).isEqualTo("demo.mp4");
        assertThat(command.video().sizeBytes()).isEqualTo(MP4.length);
    }

    @Test
    void passesNullMediaWhenPartsAreMissingSoThePolicyReportsThem() throws Exception {
        var captured = new AtomicReference<CreateProjectCommand>();
        var mockMvc = mockMvc(controller(command -> {
            captured.set(command);
            return result();
        }));

        mockMvc.perform(multipart("/").file(projectPart(MediaType.APPLICATION_JSON_VALUE)))
                .andExpect(status().isCreated());

        assertThat(captured.get().images()).isNull();
        assertThat(captured.get().video()).isNull();
    }

    @Test
    void rejectsProjectPartThatIsNotJson() throws Exception {
        mockMvc(controller(command -> result()))
                .perform(multipart("/")
                        .file(projectPart(MediaType.TEXT_PLAIN_VALUE))
                        .file(png("images")))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void rejectsPlainJsonRequests() throws Exception {
        mockMvc(controller(command -> result()))
                .perform(post("/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PROJECT_JSON))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void rejectsMissingProjectPart() throws Exception {
        mockMvc(controller(command -> result()))
                .perform(multipart("/").file(png("images")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.project").value("project part is required"));
    }

    @Test
    void validatesProjectPartFields() throws Exception {
        var invalid = new MockMultipartFile("project", "", MediaType.APPLICATION_JSON_VALUE,
                "{\"title\": \"Fiurozz Backend\"}".getBytes(StandardCharsets.UTF_8));

        mockMvc(controller(command -> result()))
                .perform(multipart("/").file(invalid).file(png("images")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.subCategoryId").exists());
    }

    @Test
    void mapsOversizedUploadsToPayloadTooLarge() {
        var response = new ApiExceptionHandler().handleMaxUploadSize(new MaxUploadSizeExceededException(1));

        assertThat(response.getStatusCode().value()).isEqualTo(413);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("PAYLOAD_TOO_LARGE");
    }

    @Test
    void publishesProjectWithTheNextEtag() {
        var result = result();
        var publishedVisibility = new java.util.concurrent.atomic.AtomicReference<
                com.philia.projectservice.catalog.internal.domain.ProjectVisibility>();
        PublishProjectUseCase publishProjectUseCase = command -> {
            publishedVisibility.set(command.visibility());
            return new ProjectDetailResult(
                result.id(), result.owner(), result.category(), result.subCategory(), result.title(), result.slug(),
                result.shortDescription(), result.description(), result.thumbnailUrl(), result.demoUrl(),
                result.techStack(), result.features(), result.tags(), "PUBLISHED", result.visibility(),
                result.sourceVisibility(), result.statistics(), Instant.parse("2026-07-28T03:00:00Z"),
                result.createdAt(), Instant.parse("2026-07-28T03:00:00Z"), 1
            );
        };
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

        var response = controller.publishProject(result.id(), "\"0\"",
                new com.philia.projectservice.catalog.internal.adapter.in.web.dto.request.PublishProjectRequest("unlisted"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getETag()).isEqualTo("\"1\"");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("PROJECT_PUBLISHED");
        assertThat(response.getBody().data().status()).isEqualTo("PUBLISHED");
        assertThat(publishedVisibility.get())
                .isEqualTo(com.philia.projectservice.catalog.internal.domain.ProjectVisibility.UNLISTED);

        controller.publishProject(result.id(), "\"0\"", null);

        assertThat(publishedVisibility.get())
                .isEqualTo(com.philia.projectservice.catalog.internal.domain.ProjectVisibility.PRIVATE);
    }

    private static ProjectCommandController controller(CreateProjectUseCase createProjectUseCase) {
        var result = result();
        return new ProjectCommandController(
                createProjectUseCase,
                Mappers.getMapper(CreateProjectWebMapper.class),
                Mappers.getMapper(ProjectDetailWebMapper.class),
                command -> new ReplaceProjectTagsResult(command.projectId(), List.of(), command.expectedVersion() + 1),
                Mappers.getMapper(ProjectTagsWebMapper.class),
                command -> result,
                Mappers.getMapper(UpdateProjectWebMapper.class),
                command -> { },
                command -> result
        );
    }

    private static MockMvc mockMvc(ProjectCommandController controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private static MockMultipartFile projectPart(String contentType) {
        return new MockMultipartFile("project", "", contentType, PROJECT_JSON.getBytes(StandardCharsets.UTF_8));
    }

    private static MockMultipartFile png(String partName) {
        return new MockMultipartFile(partName, "image.png", MediaType.IMAGE_PNG_VALUE, PNG);
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
