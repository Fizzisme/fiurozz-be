package com.philia.projectservice.catalog.internal.adapter.in.messaging;

import com.philia.projectservice.catalog.internal.adapter.in.messaging.UserEventMessages.AccountCreated;
import com.philia.projectservice.catalog.internal.adapter.in.messaging.UserEventMessages.AvatarUpdated;
import com.philia.projectservice.catalog.internal.adapter.in.messaging.UserEventMessages.ProfileUpdated;
import com.philia.projectservice.catalog.internal.application.OwnerSnapshotSyncHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Feeds user-service profile events into the owner snapshot. A malformed message is logged and
 * dropped, since retrying cannot fix it; a failure while applying a valid one is thrown so the
 * listener retries it and then dead-letters it.
 */
@Component
class OwnerSnapshotListener {

    private static final Logger log = LoggerFactory.getLogger(OwnerSnapshotListener.class);

    private final OwnerSnapshotSyncHandler syncHandler;

    OwnerSnapshotListener(OwnerSnapshotSyncHandler syncHandler) {
        this.syncHandler = syncHandler;
    }

    @RabbitListener(queues = OwnerEventsRabbitConfig.ACCOUNT_CREATED_QUEUE)
    void onAccountCreated(AccountCreated message) {
        var userId = parseUserId(message == null ? null : message.id(), "account.created");
        if (userId == null) {
            return;
        }
        if (isBlank(message.displayName())) {
            log.warn("Dropping account.created for {}: displayName is missing", userId);
            return;
        }
        syncHandler.accountCreated(userId, message.displayName().trim(), blankToNull(message.avatarUrl()));
    }

    @RabbitListener(queues = OwnerEventsRabbitConfig.PROFILE_UPDATED_QUEUE)
    void onProfileUpdated(ProfileUpdated message) {
        var userId = parseUserId(message == null ? null : message.userId(), "user.profile.updated");
        if (userId == null) {
            return;
        }
        if (isBlank(message.displayName())) {
            log.warn("Dropping user.profile.updated for {}: displayName is missing", userId);
            return;
        }
        syncHandler.displayNameChanged(userId, message.displayName().trim());
    }

    @RabbitListener(queues = OwnerEventsRabbitConfig.AVATAR_UPDATED_QUEUE)
    void onAvatarUpdated(AvatarUpdated message) {
        var userId = parseUserId(message == null ? null : message.userId(), "user.avatar.updated");
        if (userId == null) {
            return;
        }
        syncHandler.avatarChanged(userId, blankToNull(message.avatarUrl()));
    }

    private static UUID parseUserId(String raw, String event) {
        if (isBlank(raw)) {
            log.warn("Dropping {}: user id is missing", event);
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException exception) {
            log.warn("Dropping {}: '{}' is not a valid user id", event, raw);
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
