package com.philia.projectservice.files.internal.adapter.out.postgres;

import com.philia.projectservice.files.api.FilesResults;
import com.philia.projectservice.files.internal.domain.exception.FilesException;
import com.philia.projectservice.files.internal.application.port.out.FileWorkspaceStore;
import com.philia.projectservice.files.internal.domain.FileWorkspace;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** All database access uses JPA. PostgreSQL advisory locks protect S3 blob reuse from GC. */
@Repository
public class JpaFileWorkspaceStore implements FileWorkspaceStore {
    private final FileWorkspaceJpaRepository workspaces;
    private final FileRevisionJpaRepository revisions;
    private final FileRunCheckpointJpaRepository checkpoints;
    private final EntityManager entityManager;

    public JpaFileWorkspaceStore(FileWorkspaceJpaRepository workspaces, FileRevisionJpaRepository revisions,
                                 FileRunCheckpointJpaRepository checkpoints, EntityManager entityManager) {
        this.workspaces = workspaces;
        this.revisions = revisions;
        this.checkpoints = checkpoints;
        this.entityManager = entityManager;
    }

    @Override
    public FileWorkspace load(UUID projectId) {
        var result = new FileWorkspace(projectId);
        workspaces.findById(projectId).ifPresent(row -> {
            result.revision = row.revision; result.publishedRevision = row.publishedRevision;
            result.leaseId = row.leaseId; result.leaseOwnerId = row.leaseOwnerId;
            result.holder = row.holder; result.runId = row.runId; result.ttlSeconds = row.ttlSeconds;
            result.expiresAt = row.expiresAt; result.baseRevision = row.baseRevision;
        });
        return result;
    }

    @Override
    public void save(FileWorkspace state) {
        var row = workspaces.findById(state.projectId).orElseGet(FileWorkspaceJpaEntity::new);
        row.projectId = state.projectId; row.revision = state.revision;
        row.publishedRevision = state.publishedRevision; row.leaseId = state.leaseId;
        row.leaseOwnerId = state.leaseOwnerId; row.holder = state.holder;
        row.runId = state.runId; row.ttlSeconds = state.ttlSeconds;
        row.expiresAt = state.expiresAt; row.baseRevision = state.baseRevision;
        workspaces.save(row);
    }

    @Override
    public Snapshot snapshot(UUID projectId, long revision) {
        var row = revisions.findByProjectIdAndRevision(projectId, revision);
        if (row.isEmpty() && revision == 0) return new Snapshot(Map.of());
        return row.map(value -> new Snapshot(Map.copyOf(value.manifest)))
                .orElseThrow(() -> new FilesException(404, "REVISION_NOT_FOUND", "The files revision does not exist."));
    }

    @Override
    public void saveSnapshot(UUID projectId, long revision, Map<String, FilesResults.Metadata> files,
                             String label, FilesResults.Source source, int changedPaths, Instant createdAt) {
        // Revision zero is materialized once a workspace is first used.
        if (revision != 0 && revisions.findByProjectIdAndRevision(projectId, 0).isEmpty()) {
            saveSnapshot(projectId, 0, Map.of(), "Initial empty revision",
                    new FilesResults.Source("USER", null), 0, createdAt);
        }
        var row = new FileRevisionJpaEntity();
        row.id = UUID.randomUUID(); row.projectId = projectId; row.revision = revision;
        row.manifest = Map.copyOf(files); row.label = label;
        row.sourceKind = source.kind(); row.runId = source.runId();
        row.changedPaths = changedPaths; row.createdAt = createdAt;
        revisions.save(row);
    }

    @Override
    public FilesResults.Page revisions(UUID projectId, int page, int size) {
        var result = revisions.findByProjectId(projectId, PageRequest.of(page, size, Sort.by("revision").descending()));
        var items = result.map(row -> new FilesResults.Revision(row.revision, row.label,
                new FilesResults.Source(row.sourceKind, row.runId), row.changedPaths, row.createdAt)).getContent();
        return new FilesResults.Page(items, page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    public long startRun(UUID projectId, String runId, long currentRevision) {
        var row = checkpoints.findByProjectIdAndRunId(projectId, runId).orElseGet(() -> {
            var created = new FileRunCheckpointJpaEntity();
            created.id = UUID.randomUUID(); created.projectId = projectId; created.runId = runId;
            created.startRevision = currentRevision;
            return created;
        });
        checkpoints.save(row);
        return row.startRevision;
    }

    @Override
    public void endRun(UUID projectId, String runId, long currentRevision) {
        var row = checkpoints.findByProjectIdAndRunId(projectId, runId).orElseThrow();
        row.endRevision = currentRevision;
        checkpoints.save(row);
    }

    @Override
    public List<UUID> expiredLeaseProjects(Instant now) { return workspaces.findExpired(now); }

    @Override
    public void lockBlobs(List<String> hashes) {
        hashes.stream().distinct().sorted().forEach(hash -> entityManager.createNativeQuery(
                "select 1 from pg_advisory_xact_lock(hashtextextended(:key, 0))")
                .setParameter("key", "project-files:" + hash).getSingleResult());
    }

    @Override
    public boolean tryLockBlob(String hash) {
        return (Boolean) entityManager.createNativeQuery(
                "select pg_try_advisory_xact_lock(hashtextextended(:key, 0))")
                .setParameter("key", "project-files:" + hash).getSingleResult();
    }

    @Override
    public boolean isBlobReferenced(String hash) { return revisions.isBlobReferenced(hash); }
}
