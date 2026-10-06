package com.philia.projectservice.files.internal.adapter.out.postgres;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

interface FileRevisionJpaRepository extends JpaRepository<FileRevisionJpaEntity, UUID> {
    Optional<FileRevisionJpaEntity> findByProjectIdAndRevision(UUID projectId, long revision);
    Page<FileRevisionJpaEntity> findByProjectId(UUID projectId, Pageable pageable);

    @Query(value = """
            select exists(
                select 1 from project_file_revisions r,
                lateral jsonb_each(r.manifest) f
                where f.value ->> 'sha256' = :sha256
            )
            """, nativeQuery = true)
    boolean isBlobReferenced(@Param("sha256") String sha256);
}
