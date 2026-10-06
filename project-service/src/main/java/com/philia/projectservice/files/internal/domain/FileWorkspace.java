package com.philia.projectservice.files.internal.domain;

import com.philia.projectservice.files.api.FilesResults;
import com.philia.projectservice.files.internal.domain.exception.FilesException;
import java.time.Instant;
import java.util.UUID;

/** Mutable aggregate state; the PostgreSQL adapter locks the project before loading it. */
public class FileWorkspace {
    public final UUID projectId;
    public long revision;
    public Long publishedRevision;
    public UUID leaseId;
    public UUID leaseOwnerId;
    public String holder;
    public String runId;
    public int ttlSeconds;
    public Instant expiresAt;
    public long baseRevision;

    public FileWorkspace(UUID projectId) { this.projectId = projectId; }

    public boolean hasLease() { return leaseId != null; }

    public FilesResults.LeaseStatus leaseStatus() {
        return hasLease() ? new FilesResults.LeaseStatus(holder, runId, expiresAt, baseRevision) : null;
    }

    public FilesResults.Lease lease() {
        return new FilesResults.Lease(leaseId, holder, runId, expiresAt, baseRevision);
    }

    public void requireWrite(long expectedRevision, UUID suppliedLeaseId, UUID userId) {
        // Report stale data first so an expired/replaced lease never hides an overwrite.
        if (expectedRevision != revision) {
            throw new FilesException(412, "FILES_STALE_REVISION", "The files revision has changed.", revision, null);
        }
        if (hasLease() && (!leaseId.equals(suppliedLeaseId) || !leaseOwnerId.equals(userId))) {
            throw new FilesException(423, "PROJECT_LEASED", "The project is being edited.", null, leaseStatus());
        }
    }

    public void clearLease() {
        leaseId = null; leaseOwnerId = null; holder = null; runId = null;
        expiresAt = null; ttlSeconds = 0; baseRevision = 0;
    }
}
