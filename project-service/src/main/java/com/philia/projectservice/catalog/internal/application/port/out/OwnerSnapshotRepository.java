package com.philia.projectservice.catalog.internal.application.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Local copy of the owner fields that user-service owns, kept current from {@code user.events}.
 */
public interface OwnerSnapshotRepository {

    Optional<OwnerSnapshot> find(UUID userId);

    /**
     * Seeds a snapshot from {@code account.created}. Fields that are already set are kept, so a
     * redelivered or late event never overwrites a newer name or avatar.
     */
    void seed(UUID userId, String displayName, String avatarUrl);

    void changeDisplayName(UUID userId, String displayName);

    /** A {@code null} avatar URL clears the avatar. */
    void changeAvatarUrl(UUID userId, String avatarUrl);

    /** Copies the snapshot onto the owner's existing projects. Does not change a project's version. */
    void propagateToProjects(UUID userId);

    /** {@code displayName} is {@code null} until a name event or account.created has been seen. */
    record OwnerSnapshot(UUID userId, String displayName, String avatarUrl) {
    }
}
