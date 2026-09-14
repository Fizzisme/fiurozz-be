package com.philia.projectservice.catalog.internal.application;

import com.philia.projectservice.ProjectServiceApplication;
import com.philia.projectservice.shared.security.GatewayHeaderAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = ProjectServiceApplication.class,
        properties = "spring.docker.compose.enabled=false"
)
@Transactional
class ListMyProjectsPostgresIntegrationTest {

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private WebApplicationContext applicationContext;

    @Test
    void listsOnlyTheAuthenticatedOwnersActiveProjectsWithFiltersAndPagination() throws Exception {
        var ownerId = UUID.randomUUID();
        insertProject(ownerId, "Older Backend", "DRAFT", "PRIVATE", "2026-01-01T00:00:00Z", false);
        insertProject(ownerId, "Newest Backend", "DRAFT", "PRIVATE", "2026-01-03T00:00:00Z", false);
        insertProject(ownerId, "Public Catalog", "PUBLISHED", "PUBLIC", "2026-01-02T00:00:00Z", false);
        insertProject(ownerId, "Deleted Backend", "DRAFT", "PRIVATE", "2026-01-04T00:00:00Z", true);
        insertProject(UUID.randomUUID(), "Other Backend", "DRAFT", "PRIVATE", "2026-01-05T00:00:00Z", false);

        mockMvc().perform(get("/v1/me/projects")
                        .param("status", "DRAFT")
                        .param("q", "backend")
                        .param("page", "1")
                        .param("size", "1")
                        .param("sort", "createdAt,asc")
                        .header("Authorization", "Bearer test-token")
                        .header(GatewayHeaderAuthenticationFilter.USER_ID_HEADER, ownerId)
                        .header(GatewayHeaderAuthenticationFilter.USER_EMAIL_HEADER, "owner@example.com")
                        .header(GatewayHeaderAuthenticationFilter.USER_DISPLAY_NAME_HEADER, "Project Owner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("MY_PROJECTS_RETRIEVED"))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.items[0].title").value("Newest Backend"))
                .andExpect(jsonPath("$.data.items[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.data.items[0].visibility").value("PRIVATE"));
    }

    @Test
    void rejectsAnonymousAndInvalidCollectionParameters() throws Exception {
        mockMvc().perform(get("/v1/me/projects"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        var ownerId = UUID.randomUUID();
        mockMvc().perform(get("/v1/me/projects")
                        .param("size", "51")
                        .header("Authorization", "Bearer test-token")
                        .header(GatewayHeaderAuthenticationFilter.USER_ID_HEADER, ownerId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void publishesListMyProjectsOpenApiDocumentation() throws Exception {
        mockMvc().perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']['/v1/me/projects']['get']['operationId']")
                        .value("listMyProjects"))
                .andExpect(jsonPath("$['paths']['/v1/me/projects']['get']['responses']['401']")
                        .exists());
    }

    private void insertProject(
            UUID ownerId,
            String title,
            String status,
            String visibility,
            String createdAt,
            boolean deleted
    ) {
        var categoryId = UUID.randomUUID();
        var subCategoryId = UUID.randomUUID();
        var projectId = UUID.randomUUID();
        var suffix = UUID.randomUUID().toString();
        var timestamp = Instant.parse(createdAt);

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
                        INSERT INTO projects (
                            id, owner_id, owner_display_name, sub_category_id, title, slug,
                            short_description, description, tech_stack, features, status, visibility,
                            source_visibility, published_at, created_at, updated_at, deleted_at
                        ) VALUES (
                            :id, :ownerId, 'Project Owner', :subCategoryId, :title, :slug,
                            'A project catalog backend', 'The complete project description',
                            '[]'::jsonb, '[]'::jsonb, :status, :visibility, 'HIDDEN',
                            CAST(:publishedAt AS timestamptz),
                            CAST(:createdAt AS timestamptz), CAST(:createdAt AS timestamptz),
                            CAST(:deletedAt AS timestamptz)
                        )
                        """)
                .param("id", projectId)
                .param("ownerId", ownerId)
                .param("subCategoryId", subCategoryId)
                .param("title", title)
                .param("slug", "project-" + suffix)
                .param("status", status)
                .param("visibility", visibility)
                .param("publishedAt", "PUBLISHED".equals(status) ? timestamp.toString() : null)
                .param("createdAt", timestamp.toString())
                .param("deletedAt", deleted ? timestamp.toString() : null)
                .update();
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
    }
}
