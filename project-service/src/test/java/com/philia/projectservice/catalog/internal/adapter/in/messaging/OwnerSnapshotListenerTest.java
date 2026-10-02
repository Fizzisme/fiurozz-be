package com.philia.projectservice.catalog.internal.adapter.in.messaging;

import com.philia.projectservice.catalog.internal.adapter.in.messaging.UserEventMessages.AccountCreated;
import com.philia.projectservice.catalog.internal.adapter.in.messaging.UserEventMessages.AvatarUpdated;
import com.philia.projectservice.catalog.internal.adapter.in.messaging.UserEventMessages.ProfileUpdated;
import com.philia.projectservice.catalog.internal.application.OwnerSnapshotSyncHandler;
import com.philia.projectservice.catalog.internal.application.RecordingOwnerSnapshots;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.core.ParameterizedTypeReference;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OwnerSnapshotListenerTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final RecordingOwnerSnapshots snapshots = new RecordingOwnerSnapshots();
    private final OwnerSnapshotListener listener = new OwnerSnapshotListener(new OwnerSnapshotSyncHandler(snapshots));
    private final JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();

    @Test
    void readsAnOauthAccountCreatedPayloadAndIgnoresTheFieldsItDoesNotNeed() {
        var message = converter.fromMessage(json("""
                {"id":"%s","email":"owner@example.com","fullName":"Philia Owner",
                 "displayName":"Philia","avatarUrl":"https://cdn.example.com/a.png"}
                """.formatted(USER_ID)), new ParameterizedTypeReference<AccountCreated>() { });

        listener.onAccountCreated((AccountCreated) message);

        assertThat(snapshots.calls).containsExactly("seed " + USER_ID + " Philia https://cdn.example.com/a.png");
    }

    @Test
    void readsAPasswordAccountCreatedPayloadWithoutAnAvatar() {
        var message = converter.fromMessage(json("""
                {"id":"%s","email":"owner@example.com","fullName":"Philia Owner","displayName":"Philia",
                 "country":"VN","birthday":"2000-01-01T00:00:00.000Z","gender":"MALE"}
                """.formatted(USER_ID)), new ParameterizedTypeReference<AccountCreated>() { });

        listener.onAccountCreated((AccountCreated) message);

        assertThat(snapshots.calls).containsExactly("seed " + USER_ID + " Philia null");
    }

    @Test
    void readsTheUserServiceEvents() {
        var profile = converter.fromMessage(json("""
                {"userId":"%s","displayName":" New Name "}
                """.formatted(USER_ID)), new ParameterizedTypeReference<ProfileUpdated>() { });
        var avatar = converter.fromMessage(json("""
                {"userId":"%s","avatarUrl":null}
                """.formatted(USER_ID)), new ParameterizedTypeReference<AvatarUpdated>() { });

        listener.onProfileUpdated((ProfileUpdated) profile);
        listener.onAvatarUpdated((AvatarUpdated) avatar);

        assertThat(snapshots.calls).containsExactly(
                "changeDisplayName " + USER_ID + " New Name",
                "propagateToProjects " + USER_ID,
                "changeAvatarUrl " + USER_ID + " null",
                "propagateToProjects " + USER_ID);
    }

    @Test
    void dropsMalformedMessagesInsteadOfFailingThem() {
        listener.onAccountCreated(new AccountCreated("not-a-uuid", "Philia", null));
        listener.onAccountCreated(new AccountCreated(USER_ID.toString(), " ", null));
        listener.onProfileUpdated(new ProfileUpdated(null, "Philia"));
        listener.onProfileUpdated(new ProfileUpdated(USER_ID.toString(), null));
        listener.onAvatarUpdated(new AvatarUpdated("", "https://cdn.example.com/a.png"));
        listener.onAvatarUpdated(null);

        assertThat(snapshots.calls).isEmpty();
    }

    private static Message json(String body) {
        var properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }
}
