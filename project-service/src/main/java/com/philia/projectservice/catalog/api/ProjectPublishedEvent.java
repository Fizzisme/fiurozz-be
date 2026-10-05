package com.philia.projectservice.catalog.api;

import java.util.UUID;

/** Synchronous event: the files publication pointer is committed with catalog publication. */
public record ProjectPublishedEvent(UUID projectId, UUID ownerId, long expectedVersion) {}
