package com.philia.projectservice.catalog.api;

import java.util.UUID;

public record ArchiveProjectCommand(UUID projectId, long expectedVersion) {
}
