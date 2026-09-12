package com.philia.projectservice.catalog.internal.adapter.out.postgres;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface JpaProjectMediaRepository extends Repository<ProjectJpaEntity, UUID> {

    @Query(value = """
            SELECT media_url
            FROM project_media
            WHERE project_id = :projectId
              AND media_type = 'IMAGE'
            ORDER BY sort_order, created_at, id
            """, nativeQuery = true)
    List<String> findImageUrlsByProjectId(@Param("projectId") UUID projectId);
}
