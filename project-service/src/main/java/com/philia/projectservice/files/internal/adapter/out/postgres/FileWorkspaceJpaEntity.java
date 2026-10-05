package com.philia.projectservice.files.internal.adapter.out.postgres;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Files state is separate from projects.row_version and from release/project_versions. */
@Entity
@Table(name = "project_file_workspaces")
public class FileWorkspaceJpaEntity {
    @Id UUID projectId;
    long revision;
    Long publishedRevision;
    UUID leaseId;
    UUID leaseOwnerId;
    String holder;
    String runId;
    int ttlSeconds;
    Instant expiresAt;
    long baseRevision;
    protected FileWorkspaceJpaEntity() {}
}
