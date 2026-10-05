package com.philia.projectservice.files.internal.application.port.out;

import java.time.Instant;
import java.util.List;

/** Private content-addressed UTF-8 storage. A hash identifies bytes, not a public URL. */
public interface FileBlobStorage {
    void putIfAbsent(String sha256, byte[] content);
    byte[] read(String sha256);
    List<Blob> list();
    void delete(String sha256);
    record Blob(String sha256, Instant lastModified) {}
}
