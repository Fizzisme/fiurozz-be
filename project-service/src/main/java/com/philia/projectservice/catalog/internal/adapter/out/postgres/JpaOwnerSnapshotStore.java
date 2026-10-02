package com.philia.projectservice.catalog.internal.adapter.out.postgres;

import com.philia.projectservice.catalog.internal.application.port.out.OwnerSnapshotRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Reads and writes {@code owner_snapshot} with native SQL, like the media store, since the table
 * has no JPA entity of its own. Parameters are cast because a {@code null} value has no type for
 * PostgreSQL to infer.
 */
@Repository
public class JpaOwnerSnapshotStore implements OwnerSnapshotRepository {

    private final EntityManager entityManager;

    public JpaOwnerSnapshotStore(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Optional<OwnerSnapshot> find(UUID userId) {
        var rows = entityManager.createNativeQuery("""
                        SELECT display_name, avatar_url
                        FROM owner_snapshot
                        WHERE user_id = :userId
                        """)
                .setParameter("userId", userId)
                .getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        var row = (Object[]) rows.getFirst();
        return Optional.of(new OwnerSnapshot(userId, (String) row[0], (String) row[1]));
    }

    @Override
    public void seed(UUID userId, String displayName, String avatarUrl) {
        entityManager.createNativeQuery("""
                        INSERT INTO owner_snapshot (user_id, display_name, avatar_url)
                        VALUES (:userId, CAST(:displayName AS VARCHAR), CAST(:avatarUrl AS VARCHAR))
                        ON CONFLICT (user_id) DO UPDATE SET
                            display_name = COALESCE(owner_snapshot.display_name, EXCLUDED.display_name),
                            avatar_url = COALESCE(owner_snapshot.avatar_url, EXCLUDED.avatar_url),
                            updated_at = CURRENT_TIMESTAMP
                        """)
                .setParameter("userId", userId)
                .setParameter("displayName", displayName)
                .setParameter("avatarUrl", avatarUrl)
                .executeUpdate();
    }

    @Override
    public void changeDisplayName(UUID userId, String displayName) {
        entityManager.createNativeQuery("""
                        INSERT INTO owner_snapshot (user_id, display_name)
                        VALUES (:userId, CAST(:displayName AS VARCHAR))
                        ON CONFLICT (user_id) DO UPDATE SET
                            display_name = EXCLUDED.display_name,
                            updated_at = CURRENT_TIMESTAMP
                        """)
                .setParameter("userId", userId)
                .setParameter("displayName", displayName)
                .executeUpdate();
    }

    @Override
    public void changeAvatarUrl(UUID userId, String avatarUrl) {
        entityManager.createNativeQuery("""
                        INSERT INTO owner_snapshot (user_id, avatar_url)
                        VALUES (:userId, CAST(:avatarUrl AS VARCHAR))
                        ON CONFLICT (user_id) DO UPDATE SET
                            avatar_url = EXCLUDED.avatar_url,
                            updated_at = CURRENT_TIMESTAMP
                        """)
                .setParameter("userId", userId)
                .setParameter("avatarUrl", avatarUrl)
                .executeUpdate();
    }

    @Override
    public void propagateToProjects(UUID userId) {
        // owner_display_name is NOT NULL, so nothing is copied until the snapshot has a name.
        // The IS DISTINCT FROM check leaves rows untouched when a redelivered event changes nothing.
        entityManager.createNativeQuery("""
                        UPDATE projects p
                        SET owner_display_name = s.display_name,
                            owner_avatar_url = s.avatar_url
                        FROM owner_snapshot s
                        WHERE s.user_id = :userId
                          AND p.owner_id = s.user_id
                          AND s.display_name IS NOT NULL
                          AND (p.owner_display_name IS DISTINCT FROM s.display_name
                               OR p.owner_avatar_url IS DISTINCT FROM s.avatar_url)
                        """)
                .setParameter("userId", userId)
                .executeUpdate();
    }
}
