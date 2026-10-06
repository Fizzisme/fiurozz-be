package com.philia.projectservice.files.internal.application.port.out;

import com.philia.projectservice.files.api.FilesResults;
import com.philia.projectservice.files.internal.domain.FileWorkspace;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Database operations used inside one application transaction. */
public interface FileWorkspaceStore {
    FileWorkspace load(UUID projectId);
    void save(FileWorkspace workspace);
    Snapshot snapshot(UUID projectId, long revision);
    void saveSnapshot(UUID projectId, long revision, Map<String, FilesResults.Metadata> files,
                      String label, FilesResults.Source source, int changedPaths, Instant createdAt);
    FilesResults.Page revisions(UUID projectId, int page, int size);
    long startRun(UUID projectId, String runId, long currentRevision);
    void endRun(UUID projectId, String runId, long currentRevision);
    List<UUID> expiredLeaseProjects(Instant now);
    /** Sorted transaction-scoped advisory locks coordinate blob reuse with GC. */
    void lockBlobs(List<String> sha256);
    boolean tryLockBlob(String sha256);
    boolean isBlobReferenced(String sha256);
    record Snapshot(Map<String, FilesResults.Metadata> files) {}
}
