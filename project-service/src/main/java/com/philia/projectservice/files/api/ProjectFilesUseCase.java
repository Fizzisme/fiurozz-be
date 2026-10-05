package com.philia.projectservice.files.api;

import java.util.UUID;

/** Eight operations exposed by the project-files HTTP contract. */
public interface ProjectFilesUseCase {
    FilesResults.Tree getFiles(UUID projectId, Long revision, boolean includeContent);
    FilesResults.Saved applyChanges(ApplyFileChangesCommand command);
    FilesResults.Page listRevisions(UUID projectId, int page, int size);
    FilesResults.Reverted revert(RevertFilesCommand command);
    FilesResults.Lease acquireLease(AcquireLeaseCommand command);
    FilesResults.Lease renewLease(UUID projectId, UUID leaseId);
    void releaseLease(UUID projectId, UUID leaseId);
    FilesResults.LeaseStatus getLease(UUID projectId);
}
