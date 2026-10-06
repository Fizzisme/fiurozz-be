package com.philia.projectservice.files.internal.adapter.out.postgres;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

interface FileRunCheckpointJpaRepository extends JpaRepository<FileRunCheckpointJpaEntity, UUID> {
    Optional<FileRunCheckpointJpaEntity> findByProjectIdAndRunId(UUID projectId, String runId);
}
