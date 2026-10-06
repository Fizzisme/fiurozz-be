package com.philia.projectservice.files.api;

import java.util.List;
import java.util.UUID;

/** One atomic batch. expectedRevision comes from If-Match, not catalog row_version. */
public record ApplyFileChangesCommand(UUID projectId, long expectedRevision, UUID leaseId,
                                      List<Change> changes, String label, FilesResults.Source source) {
    public record Change(String op, String path, String content) {}
}
