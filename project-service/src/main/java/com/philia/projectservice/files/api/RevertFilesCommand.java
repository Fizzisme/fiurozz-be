package com.philia.projectservice.files.api;

import java.util.UUID;

/** Copies an old snapshot into a new revision; never rewrites history. */
public record RevertFilesCommand(UUID projectId, long expectedRevision, UUID leaseId,
                                 long toRevision, String label) {}
