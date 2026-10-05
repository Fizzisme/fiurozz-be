package com.philia.projectservice.files.internal.domain;

import com.philia.projectservice.files.api.FilesResults;
import com.philia.projectservice.files.internal.domain.exception.FilesException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class FilePolicyTest {
    @ParameterizedTest
    @ValueSource(strings = {"/app/page.tsx", "../a", "a/../b", "./a", "a//b", "a/", "C:/a", "a\\b", ""})
    void rejectsUnsafePaths(String path) {
        assertThatThrownBy(() -> FilePolicy.path(path)).isInstanceOf(FilesException.class)
                .extracting(error -> ((FilesException) error).code()).isEqualTo("FILE_PATH_INVALID");
    }

    @ParameterizedTest
    @ValueSource(strings = {"app/page.tsx", "app/blog/[slug]/page.tsx", "app/(marketing)/layout.tsx", ".gitignore"})
    void acceptsNextJsPaths(String path) { assertThatCode(() -> FilePolicy.path(path)).doesNotThrowAnyException(); }

    @Test
    void measuresUtf8BytesRatherThanCharacters() {
        assertThat(FilePolicy.text("ế")).hasSize(3);
        assertThatThrownBy(() -> FilePolicy.text("ế".repeat(200000))).isInstanceOf(FilesException.class)
                .extracting(error -> ((FilesException) error).status()).isEqualTo(413);
    }

    @Test
    void rejectsMalformedSurrogatesAndBinaryNull() {
        assertThatThrownBy(() -> FilePolicy.text("\uD800")).isInstanceOf(FilesException.class);
        assertThatThrownBy(() -> FilePolicy.text("a\0b")).isInstanceOf(FilesException.class);
    }

    @Test
    void enforcesManifestSizeAndFileDirectoryConflicts() {
        assertThatThrownBy(() -> FilePolicy.tree(Map.of("a", new FilesResults.Metadata("hash", FilePolicy.MAX_TREE_BYTES + 1))))
                .isInstanceOf(FilesException.class);
        assertThatThrownBy(() -> FilePolicy.tree(Map.of("app", new FilesResults.Metadata("a", 1),
                "app/page.tsx", new FilesResults.Metadata("b", 1)))).isInstanceOf(FilesException.class);
    }

    @Test
    void shaIsComputedFromContent() {
        assertThat(FilePolicy.sha256(FilePolicy.text("abc")))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
