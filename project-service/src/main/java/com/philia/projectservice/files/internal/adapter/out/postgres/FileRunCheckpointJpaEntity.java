package com.philia.projectservice.files.internal.adapter.out.postgres;

import jakarta.persistence.*;
import java.util.UUID;

/** Durable restore points do not depend on user-controlled revision labels. */
@Entity
@Table(name = "project_file_run_checkpoints")
public class FileRunCheckpointJpaEntity {
    @Id UUID id;
    UUID projectId;
    String runId;
    long startRevision;
    Long endRevision;
    protected FileRunCheckpointJpaEntity() {}
}
