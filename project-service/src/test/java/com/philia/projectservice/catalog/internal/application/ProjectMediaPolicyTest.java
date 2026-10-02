package com.philia.projectservice.catalog.internal.application;

import com.philia.projectservice.catalog.api.ProjectMediaUpload;
import com.philia.projectservice.catalog.internal.application.exception.ProjectMediaValidationException;
import com.philia.projectservice.catalog.internal.domain.ProjectMediaType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectMediaPolicyTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'E', 'B', 'P'};
    private static final byte[] GIF = {'G', 'I', 'F', '8', '9', 'a', 1, 0};
    private static final byte[] MP4 = {0, 0, 0, 0x20, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
    private static final byte[] WEBM = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, (byte) 0x9F, 0x42};
    private static final byte[] EXE = {'M', 'Z', (byte) 0x90, 0, 3, 0};

    @Test
    void detectsEachSupportedFormatAndKeepsDisplayOrder() {
        var media = ProjectMediaPolicy.validate(
                List.of(file("a.webp", WEBP), file("b.jpg", JPEG), file("c.png", PNG), file("d.gif", GIF)),
                file("demo.mp4", MP4)
        );

        assertThat(media).extracting(ProjectMediaPolicy.ValidatedMedia::contentType)
                .containsExactly("image/webp", "image/jpeg", "image/png", "image/gif", "video/mp4");
        assertThat(media).extracting(ProjectMediaPolicy.ValidatedMedia::type)
                .containsExactly(ProjectMediaType.IMAGE, ProjectMediaType.IMAGE, ProjectMediaType.IMAGE,
                        ProjectMediaType.GIF, ProjectMediaType.VIDEO);
        assertThat(media).extracting(ProjectMediaPolicy.ValidatedMedia::sortOrder)
                .containsExactly(0, 1, 2, 3, 4);
        assertThat(media.getFirst().extension()).isEqualTo("webp");
    }

    @Test
    void acceptsWebmVideoAndMissingVideo() {
        assertThat(ProjectMediaPolicy.validate(threeImages(), file("demo.webm", WEBM)).getLast().contentType())
                .isEqualTo("video/webm");
        assertThat(ProjectMediaPolicy.validate(threeImages(), null)).hasSize(3);
    }

    @Test
    void rejectsFewerThanThreeOrMoreThanFiveImages() {
        assertRejected(() -> ProjectMediaPolicy.validate(List.of(file("a.png", PNG), file("b.png", PNG)), null),
                "images", "between 3 and 5 images are required");
        assertRejected(() -> ProjectMediaPolicy.validate(images(6), null),
                "images", "between 3 and 5 images are required");
        assertRejected(() -> ProjectMediaPolicy.validate(null, null),
                "images", "between 3 and 5 images are required");
    }

    @Test
    void detectsFormatFromContentInsteadOfFilename() {
        var images = new ArrayList<>(threeImages());
        images.set(1, file("renamed.png", EXE));

        assertRejected(() -> ProjectMediaPolicy.validate(images, null),
                "images[1]", "file format is not supported");
    }

    @Test
    void rejectsVideoInImageSlotAndImageInVideoSlot() {
        var images = new ArrayList<>(threeImages());
        images.set(2, file("clip.mp4", MP4));

        assertRejected(() -> ProjectMediaPolicy.validate(images, null),
                "images[2]", "must be a JPEG, PNG, WebP or GIF image");
        assertRejected(() -> ProjectMediaPolicy.validate(threeImages(), file("poster.png", PNG)),
                "video", "must be an MP4 or WebM video");
    }

    @Test
    void enforcesSizeLimits() {
        var images = new ArrayList<>(threeImages());
        images.set(0, file("big.png", PNG, ProjectMediaPolicy.MAX_IMAGE_BYTES + 1));

        assertRejected(() -> ProjectMediaPolicy.validate(images, null),
                "images[0]", "file must not exceed 5 MB");
        assertRejected(() -> ProjectMediaPolicy.validate(
                        threeImages(), file("big.mp4", MP4, ProjectMediaPolicy.MAX_VIDEO_BYTES + 1)),
                "video", "file must not exceed 50 MB");
        assertThat(ProjectMediaPolicy.validate(
                threeImages(), file("max.mp4", MP4, ProjectMediaPolicy.MAX_VIDEO_BYTES))).hasSize(4);
    }

    @Test
    void rejectsEmptyAndMissingFiles() {
        var images = new ArrayList<>(threeImages());
        images.set(0, file("empty.png", PNG, 0));
        assertRejected(() -> ProjectMediaPolicy.validate(images, null), "images[0]", "file must not be empty");

        images.set(0, null);
        assertRejected(() -> ProjectMediaPolicy.validate(images, null), "images[0]", "file is required");
    }

    private static void assertRejected(Runnable validation, String field, String message) {
        assertThatThrownBy(validation::run)
                .isInstanceOfSatisfying(ProjectMediaValidationException.class, exception -> {
                    assertThat(exception.field()).isEqualTo(field);
                    assertThat(exception.getMessage()).isEqualTo(message);
                });
    }

    private static List<ProjectMediaUpload> threeImages() {
        return images(3);
    }

    private static List<ProjectMediaUpload> images(int count) {
        var images = new ArrayList<ProjectMediaUpload>();
        for (var index = 0; index < count; index++) {
            images.add(file("image-" + index + ".png", PNG));
        }
        return images;
    }

    private static ProjectMediaUpload file(String filename, byte[] content) {
        return file(filename, content, content.length);
    }

    private static ProjectMediaUpload file(String filename, byte[] content, long reportedSize) {
        return new ProjectMediaUpload(filename, reportedSize, () -> new ByteArrayInputStream(content));
    }
}
