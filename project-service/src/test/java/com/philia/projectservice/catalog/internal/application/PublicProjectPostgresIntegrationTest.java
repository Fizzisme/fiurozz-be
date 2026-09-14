package com.philia.projectservice.catalog.internal.application;

import com.philia.projectservice.ProjectServiceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
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
class PublicProjectPostgresIntegrationTest {

    private static final UUID ONLINE_STORE_ID =
            UUID.fromString("10000000-0000-4000-8000-000000000001");

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WebApplicationContext applicationContext;

    @Test
    void returnsFrontendCardProjectionAndContinuesWithStableCursor() throws Exception {
        insertProject("Newest project", Instant.parse("2026-09-12T03:00:00Z"));
        insertProject("Middle project", Instant.parse("2026-09-12T02:00:00Z"));
        insertProject("Oldest project", Instant.parse("2026-09-12T01:00:00Z"));

        var firstPage = mockMvc().perform(get("/")
                        .param("categorySlug", "e-commerce")
                        .param("subCategorySlug", "online-store")
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("PUBLIC_PROJECTS_RETRIEVED"))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].title").value("Newest project"))
                .andExpect(jsonPath("$.data.items[0].category.slug").value("e-commerce"))
                .andExpect(jsonPath("$.data.items[0].subCategory.slug").value("online-store"))
                .andExpect(jsonPath("$.data.items[0].techStack[0]").value("spring-boot"))
                .andExpect(jsonPath("$.data.items[0].owner.displayName").value("Project Owner"))
                .andExpect(jsonPath("$.data.hasMore").value(true))
                .andExpect(jsonPath("$.data.nextCursor").isNotEmpty())
                .andReturn();

        var cursor = objectMapper.readTree(firstPage.getResponse().getContentAsString())
                .path("data")
                .path("nextCursor")
                .asText();

        mockMvc().perform(get("/")
                        .param("categorySlug", "e-commerce")
                        .param("subCategorySlug", "online-store")
                        .param("limit", "2")
                        .param("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("Oldest project"))
                .andExpect(jsonPath("$.data.hasMore").value(false))
                .andExpect(jsonPath("$.data.nextCursor").isEmpty());
    }

    @Test
    void rejectsMalformedCursor() throws Exception {
        mockMvc().perform(get("/").param("cursor", "malformed"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void exposesSeededCatalogUsedByFrontendRoutes() throws Exception {
        mockMvc().perform(get("/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(8))
                .andExpect(jsonPath("$.data.items[0].slug").value("e-commerce"))
                .andExpect(jsonPath("$.data.items[0].icon").value("shopping-cart"));

        mockMvc().perform(get("/categories/{categoryId}/subcategories",
                        "00000000-0000-4000-8000-000000000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(4))
                .andExpect(jsonPath("$.data.items[0].slug").value("online-store"));

        mockMvc().perform(get("/tags").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PROJECT_TAGS_RETRIEVED"))
                .andExpect(jsonPath("$.data.items.length()").value(15));

        mockMvc().perform(get("/tags").param("q", "spring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].slug").value("spring-boot"));
    }

    private void insertProject(String title, Instant publishedAt) {
        var suffix = UUID.randomUUID().toString();
        jdbcClient.sql("""
                        INSERT INTO projects (
                            id, owner_id, owner_display_name, owner_avatar_url, sub_category_id,
                            title, slug, short_description, description, thumbnail_url, demo_url,
                            tech_stack, features, status, visibility, source_visibility, published_at
                        ) VALUES (
                            :id, :ownerId, 'Project Owner', 'https://cdn.example.com/owner.png', :subCategoryId,
                            :title, :slug, 'Card description', 'Full description',
                            'https://cdn.example.com/thumbnail.png', 'https://demo.example.com',
                            '["spring-boot", "next.js"]'::jsonb, '[]'::jsonb,
                            'PUBLISHED', 'PUBLIC', 'PUBLIC', :publishedAt
                        )
                        """)
                .param("id", UUID.randomUUID())
                .param("ownerId", UUID.randomUUID())
                .param("subCategoryId", ONLINE_STORE_ID)
                .param("title", title)
                .param("slug", "project-" + suffix)
                .param("publishedAt", Timestamp.from(publishedAt))
                .update();
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
    }
}
