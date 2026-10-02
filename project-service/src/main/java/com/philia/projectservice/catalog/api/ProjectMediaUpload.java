package com.philia.projectservice.catalog.api;

import java.io.IOException;
import java.io.InputStream;

/**
 * A media file received with a create-project request, independent of the HTTP multipart type.
 * The content can be opened more than once: once to detect the format, once to upload it.
 *
 * @param filename  client-supplied name, informational only; the format is detected from the content
 * @param sizeBytes size reported by the input adapter
 */
public record ProjectMediaUpload(String filename, long sizeBytes, ContentSource content) {

    @FunctionalInterface
    public interface ContentSource {
        InputStream open() throws IOException;
    }
}
