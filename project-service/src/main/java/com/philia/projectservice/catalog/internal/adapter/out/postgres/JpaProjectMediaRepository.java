package com.philia.projectservice.catalog.internal.adapter.out.postgres;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface JpaProjectMediaRepository extends Repository<ProjectJpaEntity, UUID> {

    // Aliases are quoted because PostgreSQL folds unquoted identifiers to lower case,
    // which would not match the projection getters.
    @Query(value = """
            SELECT id AS "id", media_type AS "mediaType", media_url AS "mediaUrl", sort_order AS "sortOrder"
            FROM project_media
            WHERE project_id = :projectId
            ORDER BY sort_order, created_at, id
            """, nativeQuery = true)
    List<MediaRow> findMediaByProjectId(@Param("projectId") UUID projectId);

    interface MediaRow {
        UUID getId();

        String getMediaType();

        String getMediaUrl();

        int getSortOrder();
    }
}
