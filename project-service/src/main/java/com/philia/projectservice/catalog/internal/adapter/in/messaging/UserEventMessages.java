package com.philia.projectservice.catalog.internal.adapter.in.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Payloads published by auth-service and user-service. Only the fields project-service needs are
 * declared; everything else in a payload is ignored. The shapes are not shared code, so keep them
 * in step with the publishers by hand.
 */
final class UserEventMessages {

    private UserEventMessages() {
    }

    /** {@code account.created} from auth-service. The avatar is only present for OAuth sign-ups. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record AccountCreated(String id, String displayName, String avatarUrl) {
    }

    /** {@code user.profile.updated} from user-service. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProfileUpdated(String userId, String displayName) {
    }

    /** {@code user.avatar.updated} from user-service. A {@code null} URL means the avatar was removed. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record AvatarUpdated(String userId, String avatarUrl) {
    }
}
