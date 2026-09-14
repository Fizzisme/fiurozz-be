package com.philia.projectservice.catalog.internal.application;

import com.philia.projectservice.ProjectServiceApplication;
import com.philia.projectservice.shared.security.GatewayHeaderAuthenticationFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = ProjectServiceApplication.class,
        properties = "spring.docker.compose.enabled=false"
)
@Transactional
class GetProjectByIdPostgresIntegrationTest {

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private WebApplicationContext applicationContext;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ownerRetrievesDraftProjectWithCompleteDatabaseProjection() throws Exception {
        var fixture = insertProject("DRAFT", "PRIVATE");
        insertProjectAssets(fixture);

        mockMvc().perform(get("/{projectId}", fixture.projectId())
                        .header("Authorization", "Bearer test-token")
                        .header(GatewayHeaderAuthenticationFilter.USER_ID_HEADER, fixture.ownerId())
                        .header(GatewayHeaderAuthenticationFilter.USER_EMAIL_HEADER, "owner@example.com")
                        .header(GatewayHeaderAuthenticationFilter.USER_DISPLAY_NAME_HEADER, "Project Owner"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"3\""))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("PROJECT_RETRIEVED"))
                .andExpect(jsonPath("$.data.id").value(fixture.projectId().toString()))
                .andExpect(jsonPath("$.data.owner.id").value(fixture.ownerId().toString()))
                .andExpect(jsonPath("$.data.category.id").value(fixture.categoryId().toString()))
                .andExpect(jsonPath("$.data.subCategory.id").value(fixture.subCategoryId().toString()))
                .andExpect(jsonPath("$.data.techStack[1]").value("spring-boot"))
                .andExpect(jsonPath("$.data.features[0]").value("Project catalog"))
                .andExpect(jsonPath("$.data.tags[0].id").value(fixture.tagId().toString()))
                .andExpect(jsonPath("$.data.images[0]").value("https://cdn.example.com/project-first.png"))
                .andExpect(jsonPath("$.data.images[1]").value("https://cdn.example.com/project-second.png"))
                .andExpect(jsonPath("$.data.githubUrl").value("https://github.com/philia/project"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.visibility").value("PRIVATE"))
                .andExpect(jsonPath("$.data.version").value(3));
    }

    @Test
    void anonymousCallerRetrievesPublishedUnlistedProject() throws Exception {
        var fixture = insertProject("PUBLISHED", "UNLISTED");

        mockMvc().perform(get("/{projectId}", fixture.projectId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.visibility").value("UNLISTED"));
    }

    @Test
    void hidesPrivateProjectAndDeletedProjectWithNotFoundResponse() throws Exception {
        var privateProject = insertProject("DRAFT", "PRIVATE");
        var deletedProject = insertProject("PUBLISHED", "PUBLIC");
        jdbcClient.sql("UPDATE projects SET deleted_at = CURRENT_TIMESTAMP WHERE id = :projectId")
                .param("projectId", deletedProject.projectId())
                .update();

        mockMvc().perform(get("/{projectId}", privateProject.projectId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));

        mockMvc().perform(get("/{projectId}", deletedProject.projectId())
                        .header("Authorization", "Bearer test-token")
                        .header(GatewayHeaderAuthenticationFilter.USER_ID_HEADER, deletedProject.ownerId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
    }

    @Test
    void wrapsMalformedProjectIdInApiResponse() throws Exception {
        mockMvc().perform(get("/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_PARAMETER"));
    }

    @Test
    void publishesGetProjectByIdOpenApiDocumentation() throws Exception {
        mockMvc().perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths.length()").value(12))
                .andExpect(jsonPath("$['paths']['/{projectId}']['get']['operationId']")
                        .value("getProjectById"))
                .andExpect(jsonPath("$['paths']['/{projectId}']['get']['responses']['200']")
                        .exists())
                .andExpect(jsonPath("$['paths']['/{projectId}']['get']['responses']['404']")
                        .exists());
    }

    private ProjectFixture insertProject(String status, String visibility) {
        var projectId = UUID.randomUUID();
        var ownerId = UUID.randomUUID();
        var categoryId = UUID.randomUUID();
        var subCategoryId = UUID.randomUUID();
        var tagId = UUID.randomUUID();
        var suffix = UUID.randomUUID().toString();

        jdbcClient.sql("""
                        INSERT INTO project_categories (id, key, slug, title, icon)
                        VALUES (:id, :key, :slug, :title, 'code')
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
        jdbcClient.sql("""
                        INSERT INTO tags (id, slug, display_name, normalized_name)
                        VALUES (:id, :slug, :displayName, :normalizedName)
                        """)
                .param("id", tagId)
                .param("slug", "tag-" + suffix)
                .param("displayName", "Tag " + suffix)
                .param("normalizedName", "tag-" + suffix)
                .update();
        jdbcClient.sql("""
                        INSERT INTO projects (
                            id,
                            owner_id,
                            owner_display_name,
                            sub_category_id,
                            title,
                            slug,
                            short_description,
                            description,
                            demo_url,
                            tech_stack,
                            features,
                            status,
                            visibility,
                            source_visibility,
                            published_at,
                            row_version
                        ) VALUES (
                            :id,
                            :ownerId,
                            'Project Owner',
                            :subCategoryId,
                            'Fiurozz Backend',
                            :slug,
                            'A project catalog backend',
                            'The complete project description',
                            'https://demo.example.com',
                            '["java", "spring-boot"]'::jsonb,
                            '["Project catalog"]'::jsonb,
                            :status,
                            :visibility,
                            'HIDDEN',
                            CASE WHEN :status = 'PUBLISHED' THEN CURRENT_TIMESTAMP ELSE NULL END,
                            3
                        )
                        """)
                .param("id", projectId)
                .param("ownerId", ownerId)
                .param("subCategoryId", subCategoryId)
                .param("slug", "project-" + suffix)
                .param("status", status)
                .param("visibility", visibility)
                .update();
        jdbcClient.sql("""
                        INSERT INTO project_tags (project_id, tag_id)
                        VALUES (:projectId, :tagId)
                        """)
                .param("projectId", projectId)
                .param("tagId", tagId)
                .update();

        return new ProjectFixture(projectId, ownerId, categoryId, subCategoryId, tagId);
    }

    private void insertProjectAssets(ProjectFixture fixture) {
        jdbcClient.sql("""
                        INSERT INTO project_media (id, project_id, media_type, media_url, sort_order)
                        VALUES
                            (:firstId, :projectId, 'IMAGE', 'https://cdn.example.com/project-first.png', 10),
                            (:secondId, :projectId, 'IMAGE', 'https://cdn.example.com/project-second.png', 20)
                        """)
                .param("firstId", UUID.randomUUID())
                .param("secondId", UUID.randomUUID())
                .param("projectId", fixture.projectId())
                .update();

        var integrationId = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO github_integrations (
                            id, user_id, installation_id, account_login, account_type,
                            repository_selection, permissions
                        ) VALUES (
                            :id, :userId, :installationId, 'philia', 'USER', 'SELECTED', '{}'::jsonb
                        )
                        """)
                .param("id", integrationId)
                .param("userId", fixture.ownerId())
                .param("installationId", UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE)
                .update();

        jdbcClient.sql("""
                        INSERT INTO project_repositories (
                            id, project_id, github_integration_id, github_repository_id,
                            full_name, default_branch, is_private, html_url, is_primary
                        ) VALUES (
                            :id, :projectId, :integrationId, :repositoryId,
                            'philia/project', 'main', FALSE, 'https://github.com/philia/project', TRUE
                        )
                        """)
                .param("id", UUID.randomUUID())
                .param("projectId", fixture.projectId())
                .param("integrationId", integrationId)
                .param("repositoryId", UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE)
                .update();
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
    }

    private record ProjectFixture(
            UUID projectId,
            UUID ownerId,
            UUID categoryId,
            UUID subCategoryId,
            UUID tagId
    ) {
    }
}
