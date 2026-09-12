package com.philia.projectservice.catalog.internal.adapter.out.postgres;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

interface JpaProjectCommandRepository extends JpaRepository<ProjectJpaEntity, UUID> {

    boolean existsByOwnerIdAndSlugAndDeletedAtIsNull(UUID ownerId, String slug);

    boolean existsByOwnerIdAndSlugAndDeletedAtIsNullAndIdNot(UUID ownerId, String slug, UUID projectId);

    Optional<ProjectJpaEntity> findByIdAndDeletedAtIsNull(UUID projectId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE ProjectJpaEntity project
            SET project.version = project.version + 1,
                project.updatedAt = :updatedAt
            WHERE project.id = :projectId
              AND project.ownerId = :ownerId
              AND project.version = :expectedVersion
              AND project.deletedAt IS NULL
            """)
    int advanceVersionIfCurrent(
            @Param("projectId") UUID projectId,
            @Param("ownerId") UUID ownerId,
            @Param("expectedVersion") long expectedVersion,
            @Param("updatedAt") Instant updatedAt
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE ProjectJpaEntity project
            SET project.deletedAt = :deletedAt,
                project.updatedAt = :deletedAt,
                project.version = project.version + 1
            WHERE project.id = :projectId
              AND project.ownerId = :ownerId
              AND project.version = :expectedVersion
              AND project.deletedAt IS NULL
              AND project.status IN ('DRAFT', 'ARCHIVED')
            """)
    int softDeleteIfCurrent(
            @Param("projectId") UUID projectId,
            @Param("ownerId") UUID ownerId,
            @Param("expectedVersion") long expectedVersion,
            @Param("deletedAt") Instant deletedAt
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE ProjectJpaEntity project
            SET project.status = 'PUBLISHED',
                project.publishedAt = :publishedAt,
                project.updatedAt = :publishedAt,
                project.version = project.version + 1
            WHERE project.id = :projectId
              AND project.ownerId = :ownerId
              AND project.version = :expectedVersion
              AND project.status = 'DRAFT'
              AND project.deletedAt IS NULL
            """)
    int publishDraftIfCurrent(
            @Param("projectId") UUID projectId,
            @Param("ownerId") UUID ownerId,
            @Param("expectedVersion") long expectedVersion,
            @Param("publishedAt") Instant publishedAt
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE ProjectJpaEntity project SET project.status = 'ARCHIVED', project.updatedAt = :updatedAt,
                project.version = project.version + 1
            WHERE project.id = :projectId AND project.ownerId = :ownerId AND project.version = :expectedVersion
              AND project.status IN ('DRAFT', 'PUBLISHED') AND project.deletedAt IS NULL
            """)
    int archiveIfCurrent(@Param("projectId") UUID projectId, @Param("ownerId") UUID ownerId,
                         @Param("expectedVersion") long expectedVersion, @Param("updatedAt") Instant updatedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE ProjectJpaEntity project SET project.status = 'DRAFT', project.visibility = 'PRIVATE',
                project.publishedAt = NULL, project.updatedAt = :updatedAt, project.version = project.version + 1
            WHERE project.id = :projectId AND project.ownerId = :ownerId AND project.version = :expectedVersion
              AND project.status = 'ARCHIVED' AND project.deletedAt IS NULL
            """)
    int reopenIfCurrent(@Param("projectId") UUID projectId, @Param("ownerId") UUID ownerId,
                        @Param("expectedVersion") long expectedVersion, @Param("updatedAt") Instant updatedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE ProjectJpaEntity project SET project.visibility = :visibility, project.updatedAt = :updatedAt,
                project.version = project.version + 1
            WHERE project.id = :projectId AND project.ownerId = :ownerId AND project.version = :expectedVersion
              AND project.deletedAt IS NULL
            """)
    int changeVisibilityIfCurrent(@Param("projectId") UUID projectId, @Param("ownerId") UUID ownerId,
                                  @Param("expectedVersion") long expectedVersion, @Param("visibility") String visibility,
                                  @Param("updatedAt") Instant updatedAt);
}
