package com.philia.projectservice.files.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Stable responses shared by the AI workspace adapter and the frontend. */
public final class FilesResults {
    private FilesResults() {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Source(String kind, String runId) {}
    public record Metadata(String sha256, long size) {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record File(String path, long size, String sha256, String content, Boolean deleted) {
        public File(String path, long size, String sha256, String content) {
            this(path, size, sha256, content, null);
        }
    }
    public record Tree(long revision, List<File> files) {}
    public record Saved(long revision, List<File> files) {}
    public record Reverted(long revision) {}
    public record Revision(long revision, String label, Source source, int changedPaths, Instant createdAt) {}
    public record Page(List<Revision> items, int page, int size, long totalElements, int totalPages) {}
    public record Lease(UUID leaseId, String holder, String runId, Instant expiresAt, long baseRevision) {}
    /** No secret lease ID is ever returned by the status endpoint or a lock error. */
    public record LeaseStatus(String holder, String runId, Instant expiresAt, long baseRevision) {}
}
