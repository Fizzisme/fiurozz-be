package com.philia.projectservice.catalog.internal.adapter.out.postgres;

import com.philia.projectservice.catalog.internal.application.port.out.ProjectMediaRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Writes {@code project_media} rows with native SQL, like the existing media read query,
 * since media has no JPA entity of its own.
 */
@Repository
public class JpaProjectMediaStore implements ProjectMediaRepository {

    private final EntityManager entityManager;

    public JpaProjectMediaStore(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void addAll(UUID projectId, List<NewProjectMedia> media, Instant createdAt) {
        for (var item : media) {
            entityManager.createNativeQuery("""
                            INSERT INTO project_media (
                                id, project_id, media_type, media_url, object_key, content_type,
                                size_bytes, sort_order, created_at, updated_at
                            )
                            VALUES (
                                :id, :projectId, :mediaType, :mediaUrl, :objectKey, :contentType,
                                :sizeBytes, :sortOrder, :createdAt, :createdAt
                            )
                            """)
                    .setParameter("id", item.id())
                    .setParameter("projectId", projectId)
                    .setParameter("mediaType", item.type().name())
                    .setParameter("mediaUrl", item.url())
                    .setParameter("objectKey", item.objectKey())
                    .setParameter("contentType", item.contentType())
                    .setParameter("sizeBytes", item.sizeBytes())
                    .setParameter("sortOrder", item.sortOrder())
                    .setParameter("createdAt", createdAt)
                    .executeUpdate();
        }
    }
}
