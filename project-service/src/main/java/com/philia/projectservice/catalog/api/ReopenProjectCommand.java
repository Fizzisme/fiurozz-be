package com.philia.projectservice.catalog.api;

import java.util.UUID;

public record ReopenProjectCommand(UUID projectId, long expectedVersion) {
}
