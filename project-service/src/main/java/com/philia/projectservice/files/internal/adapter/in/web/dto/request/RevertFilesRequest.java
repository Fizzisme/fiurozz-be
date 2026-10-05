package com.philia.projectservice.files.internal.adapter.in.web.dto.request;

/** A nullable Long lets validation distinguish a missing target from valid revision zero. */
public record RevertFilesRequest(Long toRevision, String label) {}
