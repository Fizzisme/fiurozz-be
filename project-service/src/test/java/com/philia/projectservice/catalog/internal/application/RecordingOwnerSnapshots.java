package com.philia.projectservice.catalog.internal.application;

import com.philia.projectservice.catalog.internal.application.port.out.OwnerSnapshotRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Test double that records each call as a readable string, in call order. */
public final class RecordingOwnerSnapshots implements OwnerSnapshotRepository {

    public final List<String> calls = new ArrayList<>();

    @Override
    public Optional<OwnerSnapshot> find(UUID userId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void seed(UUID userId, String displayName, String avatarUrl) {
        calls.add("seed " + userId + " " + displayName + " " + avatarUrl);
    }

    @Override
    public void changeDisplayName(UUID userId, String displayName) {
        calls.add("changeDisplayName " + userId + " " + displayName);
    }

    @Override
    public void changeAvatarUrl(UUID userId, String avatarUrl) {
        calls.add("changeAvatarUrl " + userId + " " + avatarUrl);
    }

    @Override
    public void propagateToProjects(UUID userId) {
        calls.add("propagateToProjects " + userId);
    }
}
