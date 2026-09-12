package com.philia.projectservice.catalog.internal.adapter.out.postgres;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface JpaProjectRepositoryLinkRepository extends Repository<ProjectJpaEntity, UUID> {

    @Query(value = """
            SELECT html_url
            FROM project_repositories
            WHERE project_id = :projectId
              AND is_primary = TRUE
              AND access_status = 'ACTIVE'
            ORDER BY created_at, id
            LIMIT 1
            """, nativeQuery = true)
    Optional<String> findPrimaryRepositoryUrl(@Param("projectId") UUID projectId);
}
