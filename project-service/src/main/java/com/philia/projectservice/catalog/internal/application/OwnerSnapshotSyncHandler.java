package com.philia.projectservice.catalog.internal.application;

import com.philia.projectservice.catalog.internal.application.port.out.OwnerSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Applies user-service profile events to the local owner snapshot and to the owner fields of the
 * user's existing projects. Every operation is idempotent because the broker may redeliver.
 */
@Service
public class OwnerSnapshotSyncHandler {

    private final OwnerSnapshotRepository snapshots;

    public OwnerSnapshotSyncHandler(OwnerSnapshotRepository snapshots) {
        this.snapshots = snapshots;
    }

    @Transactional
    public void accountCreated(UUID userId, String displayName, String avatarUrl) {
        snapshots.seed(userId, displayName, avatarUrl);
    }

    @Transactional
    public void displayNameChanged(UUID userId, String displayName) {
        snapshots.changeDisplayName(userId, displayName);
        snapshots.propagateToProjects(userId);
    }

    @Transactional
    public void avatarChanged(UUID userId, String avatarUrl) {
        snapshots.changeAvatarUrl(userId, avatarUrl);
        snapshots.propagateToProjects(userId);
    }
}
