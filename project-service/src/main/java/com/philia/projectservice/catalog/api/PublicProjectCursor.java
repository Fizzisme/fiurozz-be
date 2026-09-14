package com.philia.projectservice.catalog.api;

import com.philia.projectservice.catalog.internal.domain.exception.InvalidProjectException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Stable keyset cursor for the public project feed.
 */
public record PublicProjectCursor(Instant publishedAt, UUID projectId) {

    public PublicProjectCursor {
        if (publishedAt == null || projectId == null) {
            throw new InvalidProjectException("Cursor is invalid.");
        }
    }

    public String encode() {
        var value = publishedAt.getEpochSecond() + ":" + publishedAt.getNano() + ":" + projectId;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public static PublicProjectCursor decode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            var decoded = new String(Base64.getUrlDecoder().decode(value.trim()), StandardCharsets.UTF_8);
            var parts = decoded.split(":", 3);
            if (parts.length != 3) {
                throw new IllegalArgumentException("Unexpected cursor format");
            }
            var publishedAt = Instant.ofEpochSecond(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
            return new PublicProjectCursor(publishedAt, UUID.fromString(parts[2]));
        } catch (RuntimeException exception) {
            throw new InvalidProjectException("Cursor is invalid.");
        }
    }
}
