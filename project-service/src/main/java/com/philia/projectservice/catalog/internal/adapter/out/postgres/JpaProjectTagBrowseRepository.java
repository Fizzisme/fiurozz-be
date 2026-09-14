package com.philia.projectservice.catalog.internal.adapter.out.postgres;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface JpaProjectTagBrowseRepository extends Repository<ProjectTagJpaEntity, java.util.UUID> {
    @Query("""
            SELECT tag FROM ProjectTagJpaEntity tag
            WHERE tag.status = 'ACTIVE'
            ORDER BY tag.displayName ASC, tag.id ASC
            """)
    Page<ProjectTagJpaEntity> findActive(Pageable pageable);

    @Query("""
            SELECT tag FROM ProjectTagJpaEntity tag
            WHERE tag.status = 'ACTIVE'
              AND (lower(tag.displayName) LIKE lower(concat('%', :query, '%'))
                   OR lower(tag.normalizedName) LIKE lower(concat('%', :query, '%'))
                   OR lower(tag.slug) LIKE lower(concat('%', :query, '%')))
            ORDER BY tag.displayName ASC, tag.id ASC
            """)
    Page<ProjectTagJpaEntity> searchActive(@Param("query") String query, Pageable pageable);
}
