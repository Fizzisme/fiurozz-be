package com.philia.projectservice.files.internal.domain;

import com.philia.projectservice.files.api.FilesResults;
import com.philia.projectservice.files.internal.domain.exception.FilesException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

/** Framework-free path, text and size validation; paths are never silently normalized. */
public final class FilePolicy {
    public static final int MAX_FILE_BYTES = 512 * 1024;
    public static final int MAX_FILES = 1000;
    public static final long MAX_TREE_BYTES = 10L * 1024 * 1024;
    public static final int MAX_CHANGES = 200;

    private FilePolicy() {}

    public static void path(String path) {
        if (path == null || path.isBlank() || path.length() > 300 || path.startsWith("/")
                || path.contains("\\") || path.contains(":") || path.codePoints().anyMatch(Character::isISOControl)) {
            throw new FilesException(400, "FILE_PATH_INVALID", "A file path must be relative and at most 300 characters.");
        }
        if (!StandardCharsets.UTF_8.newEncoder().canEncode(path)) {
            throw new FilesException(400, "FILE_PATH_INVALID", "A file path must be valid UTF-8 text.");
        }
        for (var segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new FilesException(400, "FILE_PATH_INVALID", "Empty, dot and parent path segments are forbidden.");
            }
        }
    }

    public static byte[] text(String content) {
        if (content == null) throw new FilesException(400, "FILE_NOT_TEXT", "PUT requires UTF-8 text content.");
        try {
            var buffer = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(content));
            var bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            if (content.indexOf('\0') >= 0) throw new FilesException(400, "FILE_NOT_TEXT", "Binary content is not allowed.");
            if (bytes.length > MAX_FILE_BYTES) limit();
            return bytes;
        } catch (CharacterCodingException exception) {
            throw new FilesException(400, "FILE_NOT_TEXT", "Content is not valid UTF-8 text.");
        }
    }

    public static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new FilesException(503, "FILES_STORAGE_UNAVAILABLE", "Stored file content is unavailable.");
        }
    }

    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    public static void tree(Map<String, FilesResults.Metadata> files) {
        if (files.size() > MAX_FILES || files.values().stream().mapToLong(FilesResults.Metadata::size).sum() > MAX_TREE_BYTES) limit();
        // A file cannot also be an ancestor directory, e.g. "app" and "app/page.tsx".
        for (var path : files.keySet()) {
            for (int slash = path.indexOf('/'); slash >= 0; slash = path.indexOf('/', slash + 1)) {
                if (files.containsKey(path.substring(0, slash))) {
                    throw new FilesException(400, "FILE_PATH_INVALID", "A file conflicts with a directory path.");
                }
            }
        }
    }

    public static void label(String label) {
        if (label != null && label.length() > 200) invalid("Label must be at most 200 characters.");
        if (label != null && (label.indexOf('\0') >= 0 || !StandardCharsets.UTF_8.newEncoder().canEncode(label))) {
            invalid("Label must be valid UTF-8 text without NUL.");
        }
    }

    public static void source(FilesResults.Source source) {
        if (source == null || !("USER".equals(source.kind()) || "AGENT".equals(source.kind()))) invalid("Source kind must be USER or AGENT.");
        if ("AGENT".equals(source.kind())) boundedText(source.runId(), 200, "Agent runId");
        else if (source.runId() != null) invalid("USER source must not include runId.");
    }

    public static void boundedText(String value, int maximum, String field) {
        if (value == null || value.isBlank() || value.length() > maximum) invalid(field + " is required and must be at most " + maximum + " characters.");
        if (value.codePoints().anyMatch(Character::isISOControl) || !StandardCharsets.UTF_8.newEncoder().canEncode(value)) {
            invalid(field + " must be valid UTF-8 text without control characters.");
        }
    }

    public static void invalid(String message) { throw new FilesException(400, "VALIDATION_FAILED", message); }
    public static void limit() { throw new FilesException(413, "FILES_LIMIT_EXCEEDED", "The files size or count limit was exceeded."); }
}
