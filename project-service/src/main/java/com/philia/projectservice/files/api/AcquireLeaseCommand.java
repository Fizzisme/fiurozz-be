package com.philia.projectservice.files.api;

import java.util.UUID;

/** A renewable, time-bounded lock for one AI run on an owned project. */
public record AcquireLeaseCommand(UUID projectId, String holder, String runId, int ttlSeconds) {}
