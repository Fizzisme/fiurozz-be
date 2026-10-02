package com.philia.projectservice.catalog.internal.application;

import com.philia.projectservice.catalog.api.ProjectMediaUpload;
import com.philia.projectservice.catalog.internal.application.exception.ProjectMediaValidationException;
import com.philia.projectservice.catalog.internal.domain.ProjectMediaType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Validates the media sent with a new project: 3-5 images and at most one video.
 * The format is detected from the file signature; client-supplied names and content types are ignored.
 */
final class ProjectMediaPolicy {

    static final int MIN_IMAGES = 3;
    static final int MAX_IMAGES = 5;
    static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    static final long MAX_VIDEO_BYTES = 50L * 1024 * 1024;

    private static final int SIGNATURE_LENGTH = 12;

    private ProjectMediaPolicy() {
    }

    /**
     * Returns the media in display order: images in the order received, then the video.
     * The first image becomes the project thumbnail.
     */
    static List<ValidatedMedia> validate(List<ProjectMediaUpload> images, ProjectMediaUpload video) {
        if (images == null || images.size() < MIN_IMAGES || images.size() > MAX_IMAGES) {
            throw new ProjectMediaValidationException(
                    "images", "between " + MIN_IMAGES + " and " + MAX_IMAGES + " images are required");
        }

        var media = new ArrayList<ValidatedMedia>(images.size() + 1);
        for (var index = 0; index < images.size(); index++) {
            var field = "images[" + index + "]";
            var format = detect(images.get(index), field, MAX_IMAGE_BYTES, "5 MB");
            if (format.type == ProjectMediaType.VIDEO) {
                throw new ProjectMediaValidationException(field, "must be a JPEG, PNG, WebP or GIF image");
            }
            media.add(new ValidatedMedia(images.get(index), format.type, format.contentType, format.extension,
                    media.size()));
        }

        if (video != null) {
            var format = detect(video, "video", MAX_VIDEO_BYTES, "50 MB");
            if (format.type != ProjectMediaType.VIDEO) {
                throw new ProjectMediaValidationException("video", "must be an MP4 or WebM video");
            }
            media.add(new ValidatedMedia(video, format.type, format.contentType, format.extension, media.size()));
        }
        return List.copyOf(media);
    }

    private static Format detect(ProjectMediaUpload upload, String field, long maximumBytes, String maximumLabel) {
        if (upload == null || upload.content() == null) {
            throw new ProjectMediaValidationException(field, "file is required");
        }
        if (upload.sizeBytes() <= 0) {
            throw new ProjectMediaValidationException(field, "file must not be empty");
        }
        if (upload.sizeBytes() > maximumBytes) {
            throw new ProjectMediaValidationException(field, "file must not exceed " + maximumLabel);
        }
        return Format.fromSignature(readSignature(upload))
                .orElseThrow(() -> new ProjectMediaValidationException(field, "file format is not supported"));
    }

    private static byte[] readSignature(ProjectMediaUpload upload) {
        try (var input = upload.content().open()) {
            return input.readNBytes(SIGNATURE_LENGTH);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read uploaded media", exception);
        }
    }

    record ValidatedMedia(
            ProjectMediaUpload upload,
            ProjectMediaType type,
            String contentType,
            String extension,
            int sortOrder
    ) {
    }

    private enum Format {
        JPEG(ProjectMediaType.IMAGE, "image/jpeg", "jpg"),
        PNG(ProjectMediaType.IMAGE, "image/png", "png"),
        WEBP(ProjectMediaType.IMAGE, "image/webp", "webp"),
        GIF(ProjectMediaType.GIF, "image/gif", "gif"),
        MP4(ProjectMediaType.VIDEO, "video/mp4", "mp4"),
        WEBM(ProjectMediaType.VIDEO, "video/webm", "webm");

        private final ProjectMediaType type;
        private final String contentType;
        private final String extension;

        Format(ProjectMediaType type, String contentType, String extension) {
            this.type = type;
            this.contentType = contentType;
            this.extension = extension;
        }

        static Optional<Format> fromSignature(byte[] header) {
            if (startsWith(header, 0, 0xFF, 0xD8, 0xFF)) {
                return Optional.of(JPEG);
            }
            if (startsWith(header, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
                return Optional.of(PNG);
            }
            if (startsWith(header, 0, 'R', 'I', 'F', 'F') && startsWith(header, 8, 'W', 'E', 'B', 'P')) {
                return Optional.of(WEBP);
            }
            if (startsWith(header, 0, 'G', 'I', 'F', '8', '7', 'a')
                    || startsWith(header, 0, 'G', 'I', 'F', '8', '9', 'a')) {
                return Optional.of(GIF);
            }
            // ISO base media: a 4-byte box size followed by the "ftyp" box type.
            if (startsWith(header, 4, 'f', 't', 'y', 'p')) {
                return Optional.of(MP4);
            }
            // EBML header shared by WebM and Matroska.
            if (startsWith(header, 0, 0x1A, 0x45, 0xDF, 0xA3)) {
                return Optional.of(WEBM);
            }
            return Optional.empty();
        }

        private static boolean startsWith(byte[] header, int offset, int... expected) {
            if (header.length < offset + expected.length) {
                return false;
            }
            return Arrays.equals(
                    Arrays.copyOfRange(header, offset, offset + expected.length),
                    toBytes(expected)
            );
        }

        private static byte[] toBytes(int[] values) {
            var bytes = new byte[values.length];
            for (var index = 0; index < values.length; index++) {
                bytes[index] = (byte) values[index];
            }
            return bytes;
        }
    }
}
