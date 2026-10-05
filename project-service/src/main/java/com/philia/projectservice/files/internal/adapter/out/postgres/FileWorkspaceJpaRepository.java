package com.philia.projectservice.files.internal.adapter.out.postgres;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface FileWorkspaceJpaRepository extends JpaRepository<FileWorkspaceJpaEntity, UUID> {
    @Query("select w.projectId from FileWorkspaceJpaEntity w where w.leaseId is not null and w.expiresAt <= :now")
    List<UUID> findExpired(@Param("now") Instant now);
}
