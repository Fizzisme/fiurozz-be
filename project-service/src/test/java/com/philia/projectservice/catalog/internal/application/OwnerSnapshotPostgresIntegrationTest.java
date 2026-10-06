package com.philia.projectservice.catalog.internal.application;

import com.philia.projectservice.ProjectServiceApplication;
import com.philia.projectservice.catalog.api.CreateProjectCommand;
import com.philia.projectservice.catalog.api.CreateProjectUseCase;
import com.philia.projectservice.catalog.api.ProjectMediaUpload;
import com.philia.projectservice.catalog.internal.application.port.out.OwnerSnapshotRepository;
import com.philia.projectservice.catalog.internal.application.port.out.ProjectMediaStorage;
import com.philia.projectservice.shared.security.GatewayActorPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = ProjectServiceApplication.class,
        properties = "spring.docker.compose.enabled=false"
)
@Transactional
class OwnerSnapshotPostgresIntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};

    @Autowired
    private OwnerSnapshotSyncHandler syncHandler;

    @Autowired
    private OwnerSnapshotRepository snapshots;

    @Autowired
    private CreateProjectUseCase createProjectUseCase;

    @Autowired
    private JdbcClient jdbcClient;

    // Replaces MinIO: these tests verify persistence, not object storage.
    @MockitoBean
    private ProjectMediaStorage mediaStorage;

    @BeforeEach
    void stubMediaStorage() {
        when(mediaStorage.publicUrl(any())).thenAnswer(invocation -> "http://minio.test/" + invocation.getArgument(0));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void seedingKeepsFieldsThatAreAlreadySet() {
        var userId = UUID.randomUUID();

        syncHandler.accountCreated(userId, "First Name", "http://minio.test/avatars/first.webp");
        syncHandler.accountCreated(userId, "Redelivered Name", "http://minio.test/avatars/other.webp");

        assertThat(snapshots.find(userId)).hasValueSatisfying(snapshot -> {
            assertThat(snapshot.displayName()).isEqualTo("First Name");
            assertThat(snapshot.avatarUrl()).isEqualTo("http://minio.test/avatars/first.webp");
        });
    }

    @Test
    void anAvatarEventBeforeAccountCreatedIsKeptAndTheNameIsFilledInLater() {
        var userId = UUID.randomUUID();

        syncHandler.avatarChanged(userId, "http://minio.test/avatars/new.webp");
        assertThat(snapshots.find(userId)).hasValueSatisfying(snapshot -> assertThat(snapshot.displayName()).isNull());

        syncHandler.accountCreated(userId, "Philia", "http://minio.test/avatars/oauth.png");

        assertThat(snapshots.find(userId)).hasValueSatisfying(snapshot -> {
            assertThat(snapshot.displayName()).isEqualTo("Philia");
            assertThat(snapshot.avatarUrl()).isEqualTo("http://minio.test/avatars/new.webp");
        });
    }

    @Test
    void aNullAvatarClearsTheAvatar() {
        var userId = UUID.randomUUID();
        syncHandler.accountCreated(userId, "Philia", "http://minio.test/avatars/a.webp");

        syncHandler.avatarChanged(userId, null);

        assertThat(snapshots.find(userId)).hasValueSatisfying(snapshot -> {
            assertThat(snapshot.displayName()).isEqualTo("Philia");
            assertThat(snapshot.avatarUrl()).isNull();
        });
    }

    @Test
    void profileEventsUpdateTheOwnerFieldsOfExistingProjectsWithoutChangingTheirVersion() {
        var ownerId = UUID.randomUUID();
        var subCategoryId = insertReferences();
        syncHandler.accountCreated(ownerId, "Old Name", "http://minio.test/avatars/old.webp");
        authenticate(ownerId);
        var created = createProjectUseCase.create(new CreateProjectCommand(
                subCategoryId, "Owner Sync " + UUID.randomUUID(), "A project catalog backend",
                "The complete project description", null, null, "PRIVATE",
                List.of(), List.of(), List.of(), images(), null));

        syncHandler.displayNameChanged(ownerId, "New Name");
        syncHandler.avatarChanged(ownerId, "http://minio.test/avatars/new.webp");

        var row = jdbcClient.sql("""
                        SELECT owner_display_name, owner_avatar_url, row_version
                        FROM projects
                        WHERE id = :projectId
                        """)
                .param("projectId", created.id())
                .query((rs, rowNum) -> List.of(rs.getString(1), rs.getString(2), rs.getString(3)))
                .single();
        assertThat(row).containsExactly("New Name", "http://minio.test/avatars/new.webp", "0");
    }

    @Test
    void doesNotTouchProjectsOfOtherOwners() {
        var ownerId = UUID.randomUUID();
        var otherOwnerId = UUID.randomUUID();
        var subCategoryId = insertReferences();
        syncHandler.accountCreated(ownerId, "Owner", null);
        syncHandler.accountCreated(otherOwnerId, "Other", null);
        authenticate(ownerId);
        var created = createProjectUseCase.create(new CreateProjectCommand(
                subCategoryId, "Owner Sync " + UUID.randomUUID(), "A project catalog backend",
                "The complete project description", null, null, "PRIVATE",
                List.of(), List.of(), List.of(), images(), null));

        syncHandler.displayNameChanged(otherOwnerId, "Other Renamed");

        var ownerDisplayName = jdbcClient.sql("SELECT owner_display_name FROM projects WHERE id = :projectId")
                .param("projectId", created.id())
                .query(String.class)
                .single();
        assertThat(ownerDisplayName).isEqualTo("Owner");
    }

    private UUID insertReferences() {
        var categoryId = UUID.randomUUID();
        var subCategoryId = UUID.randomUUID();
        var suffix = UUID.randomUUID().toString();
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
        return subCategoryId;
    }

    private static List<ProjectMediaUpload> images() {
        return List.of(png(), png(), png());
    }

    private static ProjectMediaUpload png() {
        return new ProjectMediaUpload("image.png", PNG.length, () -> new ByteArrayInputStream(PNG));
    }

    private static void authenticate(UUID ownerId) {
        var principal = new GatewayActorPrincipal(ownerId, "owner@example.com", "Project Owner", null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
}
