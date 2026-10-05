package com.philia.projectservice.files.internal.adapter.out.postgres;

import com.philia.projectservice.files.api.FilesResults;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Immutable complete manifest; content is kept privately in S3, not in this row. */
@Entity
@Table(name = "project_file_revisions")
public class FileRevisionJpaEntity {
    @Id UUID id;
    UUID projectId;
    long revision;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    Map<String, FilesResults.Metadata> manifest;
    String label;
    String sourceKind;
    String runId;
    int changedPaths;
    Instant createdAt;
    protected FileRevisionJpaEntity() {}
}
