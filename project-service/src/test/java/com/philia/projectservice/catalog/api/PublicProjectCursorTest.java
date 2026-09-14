package com.philia.projectservice.catalog.api;

import com.philia.projectservice.catalog.internal.domain.exception.InvalidProjectException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicProjectCursorTest {

    @Test
    void encodesAndDecodesWithoutLosingOrderingValues() {
        var cursor = new PublicProjectCursor(
                Instant.parse("2026-09-12T04:30:15.123456789Z"),
                UUID.fromString("5dc3fc50-e01a-499b-9fbc-5d107aa9f459")
        );

        assertThat(PublicProjectCursor.decode(cursor.encode())).isEqualTo(cursor);
    }

    @Test
    void rejectsMalformedCursor() {
        assertThatThrownBy(() -> PublicProjectCursor.decode("not-a-cursor"))
                .isInstanceOf(InvalidProjectException.class)
                .hasMessage("Cursor is invalid.");
    }
}
