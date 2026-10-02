package com.philia.projectservice.catalog.internal.application;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OwnerSnapshotSyncHandlerTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final RecordingOwnerSnapshots snapshots = new RecordingOwnerSnapshots();
    private final OwnerSnapshotSyncHandler handler = new OwnerSnapshotSyncHandler(snapshots);

    @Test
    void seedsTheSnapshotWhenAnAccountIsCreated() {
        handler.accountCreated(USER_ID, "Philia", "https://cdn.example.com/a.png");

        assertThat(snapshots.calls).containsExactly("seed " + USER_ID + " Philia https://cdn.example.com/a.png");
    }

    @Test
    void updatesTheSnapshotThenTheExistingProjectsWhenTheDisplayNameChanges() {
        handler.displayNameChanged(USER_ID, "New Name");

        assertThat(snapshots.calls).containsExactly(
                "changeDisplayName " + USER_ID + " New Name",
                "propagateToProjects " + USER_ID);
    }

    @Test
    void updatesTheSnapshotThenTheExistingProjectsWhenTheAvatarChanges() {
        handler.avatarChanged(USER_ID, null);

        assertThat(snapshots.calls).containsExactly(
                "changeAvatarUrl " + USER_ID + " null",
                "propagateToProjects " + USER_ID);
    }
}
