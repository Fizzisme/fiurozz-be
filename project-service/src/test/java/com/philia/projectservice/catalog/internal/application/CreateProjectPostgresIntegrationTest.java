package com.philia.projectservice.catalog.internal.application;

import com.philia.projectservice.ProjectServiceApplication;
import com.philia.projectservice.catalog.api.CreateProjectCommand;
import com.philia.projectservice.catalog.api.CreateProjectUseCase;
import com.philia.projectservice.catalog.internal.application.port.out.CatalogBrowseQuery;
import com.philia.projectservice.shared.security.GatewayHeaderAuthenticationFilter;
import com.philia.projectservice.shared.security.GatewayActorPrincipal;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = ProjectServiceApplication.class,
        properties = "spring.docker.compose.enabled=false"
)
@Transactional
class CreateProjectPostgresIntegrationTest {

    @Autowired
    private CreateProjectUseCase createProjectUseCase;

    @Autowired
    private CatalogBrowseQuery catalogBrowseQuery;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void persistsProjectAndTagsInPostgres() {
        var ownerId = UUID.randomUUID();
        var categoryId = UUID.randomUUID();
        var subCategoryId = UUID.randomUUID();
        var tagId = UUID.randomUUID();
        var suffix = UUID.randomUUID().toString();
        insertReferences(categoryId, subCategoryId, tagId, suffix);
        authenticate(ownerId);

        var result = createProjectUseCase.create(new CreateProjectCommand(
                subCategoryId,
                "Fiurozz Backend " + suffix,
                "A project catalog backend",
                "The complete project description",
                "https://demo.example.com",
                "https://github.com/fizzisme/fiurozz-be",
                "PRIVATE",
                List.of("Java", "Spring Boot"),
                List.of("Project Catalog"),
                List.of(tagId)
        ));

        var projectCount = ((Number) entityManager.createNativeQuery("""
                        SELECT COUNT(*)
                        FROM projects
                        WHERE id = :projectId
                          AND owner_id = :ownerId
                          AND status = 'DRAFT'
                          AND visibility = 'PRIVATE'
                          AND source_visibility = 'HIDDEN'
                          AND tech_stack = '["java", "spring-boot"]'::jsonb
                          AND repository_url = 'https://github.com/fizzisme/fiurozz-be'
                        """)
                .setParameter("projectId", result.id())
                .setParameter("ownerId", ownerId)
                .getSingleResult()).longValue();
        var tagCount = ((Number) entityManager.createNativeQuery("""
                        SELECT COUNT(*)
                        FROM project_tags
                        WHERE project_id = :projectId
                          AND tag_id = :tagId
                        """)
                .setParameter("projectId", result.id())
                .setParameter("tagId", tagId)
                .getSingleResult()).longValue();

        assertThat(projectCount).isEqualTo(1);
        assertThat(tagCount).isEqualTo(1);
        assertThat(result.tags()).singleElement().satisfies(tag -> assertThat(tag.id()).isEqualTo(tagId));
    }

    @Test
    void loadsCategoryTreeFromPostgres() {
        var categoryId = UUID.randomUUID();
        var subCategoryId = UUID.randomUUID();
        var tagId = UUID.randomUUID();
        var suffix = UUID.randomUUID().toString();
        insertReferences(categoryId, subCategoryId, tagId, suffix);

        var tree = catalogBrowseQuery.listCategoryTree();

        assertThat(tree)
                .filteredOn(category -> category.id().equals(categoryId))
                .singleElement()
                .satisfies(category -> assertThat(category.subCategories()).singleElement().satisfies(subCategory -> {
                    assertThat(subCategory.id()).isEqualTo(subCategoryId);
                    assertThat(subCategory.slug()).isEqualTo("subcategory-" + suffix);
                }));
    }

    @Test
    void createsProjectThroughHttpApi() throws Exception {
        var ownerId = UUID.randomUUID();
        var categoryId = UUID.randomUUID();
        var subCategoryId = UUID.randomUUID();
        var tagId = UUID.randomUUID();
        var suffix = UUID.randomUUID().toString();
        insertReferences(categoryId, subCategoryId, tagId, suffix);

        var requestBody = """
                {
                  "subCategoryId": "%s",
                  "title": "Fiurozz Backend %s",
                  "shortDescription": "A project catalog backend",
                  "description": "The complete project description",
                  "demoUrl": "https://demo.example.com",
                  "githubUrl": "https://github.com/fizzisme/fiurozz-be",
                  "visibility": "PRIVATE",
                  "techStack": ["Java", "Spring Boot"],
                  "features": ["Project Catalog"],
                  "tagIds": ["%s"]
                }
                """.formatted(subCategoryId, suffix, tagId);

        var mvcResult = mockMvc().perform(post("/")
                        .header("Authorization", "Bearer test-token")
                        .header(GatewayHeaderAuthenticationFilter.USER_ID_HEADER, ownerId)
                        .header(GatewayHeaderAuthenticationFilter.USER_EMAIL_HEADER, "owner@example.com")
                        .header(GatewayHeaderAuthenticationFilter.USER_DISPLAY_NAME_HEADER, "Project Owner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("PROJECT_CREATED"))
                .andExpect(jsonPath("$.data.owner.id").value(ownerId.toString()))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.visibility").value("PRIVATE"))
                .andExpect(jsonPath("$.data.sourceVisibility").value("HIDDEN"))
                .andExpect(jsonPath("$.data.githubUrl").value("https://github.com/fizzisme/fiurozz-be"))
                .andExpect(jsonPath("$.data.techStack[1]").value("spring-boot"))
                .andExpect(jsonPath("$.data.tags[0].id").value(tagId.toString()))
                .andReturn();

        var responseJson = objectMapper.readTree(mvcResult.getResponse().getContentAsByteArray());
        var projectId = UUID.fromString(responseJson.path("data").path("id").asText());
        var projectCount = jdbcClient.sql("SELECT COUNT(*) FROM projects WHERE id = :projectId")
                .param("projectId", projectId)
                .query(Long.class)
                .single();

        assertThat(projectCount).isEqualTo(1);
    }

    @Test
    void rejectsNonGithubRepositoryUrlThroughHttpApi() throws Exception {
        var requestBody = """
                {
                  "subCategoryId": "%s",
                  "title": "Fiurozz Backend",
                  "shortDescription": "A project catalog backend",
                  "description": "The complete project description",
                  "githubUrl": "https://gitlab.com/fizzisme/fiurozz-be"
                }
                """.formatted(UUID.randomUUID());

        mockMvc().perform(post("/")
                        .header("Authorization", "Bearer test-token")
                        .header(GatewayHeaderAuthenticationFilter.USER_ID_HEADER, UUID.randomUUID())
                        .header(GatewayHeaderAuthenticationFilter.USER_EMAIL_HEADER, "owner@example.com")
                        .header(GatewayHeaderAuthenticationFilter.USER_DISPLAY_NAME_HEADER, "Project Owner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.githubUrl").value("githubUrl must be a valid GitHub HTTPS URL"));
    }

    @Test
    void replacesProjectTagsThroughHttpApiAndIncrementsEtag() throws Exception {
        var ownerId = UUID.randomUUID();
        var categoryId = UUID.randomUUID();
        var subCategoryId = UUID.randomUUID();
        var originalTagId = UUID.randomUUID();
        var replacementTagId = UUID.randomUUID();
        var suffix = UUID.randomUUID().toString();
        insertReferences(categoryId, subCategoryId, originalTagId, suffix);
        insertTag(replacementTagId, suffix + "-replacement");
        authenticate(ownerId);

        var created = createProjectUseCase.create(new CreateProjectCommand(
                subCategoryId,
                "Fiurozz Backend " + suffix,
                "A project catalog backend",
                "The complete project description",
                "https://demo.example.com",
                null,
                "PRIVATE",
                List.of("Java"),
                List.of("Project Catalog"),
                List.of(originalTagId)
        ));

        mockMvc().perform(put("/{projectId}/tags", created.id())
                        .header("Authorization", "Bearer test-token")
                        .header(GatewayHeaderAuthenticationFilter.USER_ID_HEADER, ownerId)
                        .header(GatewayHeaderAuthenticationFilter.USER_EMAIL_HEADER, "owner@example.com")
                        .header(GatewayHeaderAuthenticationFilter.USER_DISPLAY_NAME_HEADER, "Project Owner")
                        .header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tagIds\":[\"" + replacementTagId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("PROJECT_TAGS_REPLACED"))
                .andExpect(jsonPath("$.data.projectId").value(created.id().toString()))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.tags[0].id").value(replacementTagId.toString()));

        var tagCount = ((Number) entityManager.createNativeQuery("""
                        SELECT COUNT(*)
                        FROM project_tags
                        WHERE project_id = :projectId
                          AND tag_id = :tagId
                        """)
                .setParameter("projectId", created.id())
                .setParameter("tagId", replacementTagId)
                .getSingleResult()).longValue();
        assertThat(tagCount).isEqualTo(1);
    }

    @Test
    void updatesOwnerProjectThroughHttpApiAndIncrementsEtag() throws Exception {
        var ownerId = UUID.randomUUID();
        var categoryId = UUID.randomUUID();
        var subCategoryId = UUID.randomUUID();
        var tagId = UUID.randomUUID();
        var suffix = UUID.randomUUID().toString();
        insertReferences(categoryId, subCategoryId, tagId, suffix);
        authenticate(ownerId);

        var created = createProjectUseCase.create(new CreateProjectCommand(
                subCategoryId, "Fiurozz Backend " + suffix,
                "A project catalog backend", "The complete project description", "https://demo.example.com", null,
                "PRIVATE", List.of("Java"), List.of("Project Catalog"), List.of(tagId)
        ));

        mockMvc().perform(patch("/{projectId}", created.id())
                        .header("Authorization", "Bearer test-token")
                        .header(GatewayHeaderAuthenticationFilter.USER_ID_HEADER, ownerId)
                        .header(GatewayHeaderAuthenticationFilter.USER_EMAIL_HEADER, "owner@example.com")
                        .header(GatewayHeaderAuthenticationFilter.USER_DISPLAY_NAME_HEADER, "Project Owner")
                        .header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated Fiurozz Backend\",\"techStack\":[\"Java\",\"Spring Boot\"]}"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("PROJECT_UPDATED"))
                .andExpect(jsonPath("$.data.title").value("Updated Fiurozz Backend"))
                .andExpect(jsonPath("$.data.slug").value(created.slug()))
                .andExpect(jsonPath("$.data.techStack[1]").value("spring-boot"))
                .andExpect(jsonPath("$.data.version").value(1));

        var version = jdbcClient.sql("SELECT row_version FROM projects WHERE id = :projectId")
                .param("projectId", created.id())
                .query(Long.class)
                .single();
        assertThat(version).isEqualTo(1L);
    }

    @Test
    void softDeletesDraftProjectThroughHttpApi() throws Exception {
        var ownerId = UUID.randomUUID();
        var categoryId = UUID.randomUUID();
        var subCategoryId = UUID.randomUUID();
        var tagId = UUID.randomUUID();
        var suffix = UUID.randomUUID().toString();
        insertReferences(categoryId, subCategoryId, tagId, suffix);
        authenticate(ownerId);

        var created = createProjectUseCase.create(new CreateProjectCommand(
                subCategoryId, "Fiurozz Backend " + suffix,
                "A project catalog backend", "The complete project description", "https://demo.example.com", null,
                "PRIVATE", List.of("Java"), List.of("Project Catalog"), List.of(tagId)
        ));

        mockMvc().perform(delete("/{projectId}", created.id())
                        .header("Authorization", "Bearer test-token")
                        .header(GatewayHeaderAuthenticationFilter.USER_ID_HEADER, ownerId)
                        .header(GatewayHeaderAuthenticationFilter.USER_EMAIL_HEADER, "owner@example.com")
                        .header(GatewayHeaderAuthenticationFilter.USER_DISPLAY_NAME_HEADER, "Project Owner")
                        .header("If-Match", "\"0\""))
                .andExpect(status().isNoContent());

        var deleted = jdbcClient.sql("SELECT deleted_at IS NOT NULL FROM projects WHERE id = :projectId")
                .param("projectId", created.id())
                .query(Boolean.class)
                .single();
        var version = jdbcClient.sql("SELECT row_version FROM projects WHERE id = :projectId")
                .param("projectId", created.id())
                .query(Long.class)
                .single();
        assertThat(deleted).isTrue();
        assertThat(version).isEqualTo(1L);
    }

    @Test
    void publishesOwnedDraftProjectThroughHttpApi() throws Exception {
        var ownerId = UUID.randomUUID();
        var categoryId = UUID.randomUUID();
        var subCategoryId = UUID.randomUUID();
        var tagId = UUID.randomUUID();
        var suffix = UUID.randomUUID().toString();
        insertReferences(categoryId, subCategoryId, tagId, suffix);
        authenticate(ownerId);

        var created = createProjectUseCase.create(new CreateProjectCommand(
                subCategoryId, "Fiurozz Backend " + suffix,
                "A project catalog backend", "The complete project description", "https://demo.example.com", null,
                "PUBLIC", List.of("Java"), List.of("Project Catalog"), List.of(tagId)
        ));

        mockMvc().perform(post("/{projectId}/publish", created.id())
                        .header("Authorization", "Bearer test-token")
                        .header(GatewayHeaderAuthenticationFilter.USER_ID_HEADER, ownerId)
                        .header(GatewayHeaderAuthenticationFilter.USER_EMAIL_HEADER, "owner@example.com")
                        .header(GatewayHeaderAuthenticationFilter.USER_DISPLAY_NAME_HEADER, "Project Owner")
                        .header("If-Match", "\"0\""))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("PROJECT_PUBLISHED"))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.publishedAt").exists())
                .andExpect(jsonPath("$.data.version").value(1));

        var state = jdbcClient.sql("""
                        SELECT status, published_at IS NOT NULL AS published, row_version
                        FROM projects WHERE id = :projectId
                        """)
                .param("projectId", created.id())
                .query((resultSet, rowNumber) -> List.of(
                        resultSet.getString("status"),
                        Boolean.toString(resultSet.getBoolean("published")),
                        Long.toString(resultSet.getLong("row_version"))
                ))
                .single();
        assertThat(state).containsExactly("PUBLISHED", "true", "1");
    }

    @Test
    void wrapsMissingAuthenticationInApiResponse() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc().perform(post("/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void publishesCreateProjectOpenApiDocumentation() throws Exception {
        mockMvc().perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Fiurozz Project Service API"))
                .andExpect(jsonPath("$.paths.length()").value(13))
                .andExpect(jsonPath("$['paths']['/categories/tree']['get']['operationId']")
                        .value("listCategoryTree"))
                .andExpect(jsonPath("$['paths']['/']['post']['operationId']")
                        .value("createProject"))
                .andExpect(jsonPath("$['paths']['/']['post']['security'][0]['bearerAuth']")
                        .exists())
                .andExpect(jsonPath("$['paths']['/{projectId}/tags']['put']['operationId']")
                        .value("replaceProjectTags"))
                .andExpect(jsonPath("$['paths']['/{projectId}']['patch']['operationId']")
                        .value("updateProject"))
                .andExpect(jsonPath("$['paths']['/{projectId}']['delete']['operationId']")
                        .value("deleteProject"))
                .andExpect(jsonPath("$['paths']['/{projectId}/publish']['post']['operationId']")
                        .value("publishProject"))
                .andExpect(jsonPath("$['paths']['/']['post']['responses']['201']")
                        .exists())
                .andExpect(jsonPath("$['components']['schemas']['CreateProjectRequest']['properties']['slug']")
                        .doesNotExist())
                .andExpect(jsonPath("$['components']['schemas']['UpdateProjectRequest']['properties']['slug']")
                        .doesNotExist())
                .andExpect(jsonPath("$['components']['securitySchemes']['bearerAuth']['scheme']")
                        .value("bearer"));
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
    }

    private void insertReferences(UUID categoryId, UUID subCategoryId, UUID tagId, String suffix) {
        jdbcClient.sql("""
                        INSERT INTO project_categories (id, key, slug, title)
                        VALUES (:id, :key, :slug, :title)
                        """)
                .param("id", categoryId)
                .param("key", "category-" + suffix)
                .param("slug", "category-" + suffix)
                .param("title", "Category " + suffix)
                .update();
        jdbcClient.sql("""
                        INSERT INTO project_sub_categories (id, category_id, key, slug, title)
                        VALUES (:id, :categoryId, :key, :slug, :title)
                        """)
                .param("id", subCategoryId)
                .param("categoryId", categoryId)
                .param("key", "subcategory-" + suffix)
                .param("slug", "subcategory-" + suffix)
                .param("title", "Subcategory " + suffix)
                .update();
        insertTag(tagId, suffix);
    }

    private void insertTag(UUID tagId, String suffix) {
        jdbcClient.sql("""
                        INSERT INTO tags (id, slug, display_name, normalized_name)
                        VALUES (:id, :slug, :displayName, :normalizedName)
                        """)
                .param("id", tagId)
                .param("slug", "tag-" + suffix)
                .param("displayName", "Tag " + suffix)
                .param("normalizedName", "tag-" + suffix)
                .update();
    }

    private static void authenticate(UUID ownerId) {
        var principal = new GatewayActorPrincipal(ownerId, "owner@example.com", "Project Owner", null);
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
