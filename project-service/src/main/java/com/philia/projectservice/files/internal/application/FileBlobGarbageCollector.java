package com.philia.projectservice.files.internal.application;

import com.philia.projectservice.files.internal.application.port.out.FileWorkspaceStore;
import com.philia.projectservice.files.internal.application.port.out.FileBlobStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Duration;

/** Per-object transaction and lock: a concurrent writer cannot reuse a blob during deletion. */
@Service
public class FileBlobGarbageCollector {
    private final FileWorkspaceStore store;
    private final FileBlobStorage blobs;
    private final Clock clock;

    public FileBlobGarbageCollector(FileWorkspaceStore store, FileBlobStorage blobs, Clock clock) {
        this.store = store; this.blobs = blobs; this.clock = clock;
    }

    @Transactional
    public boolean collect(FileBlobStorage.Blob blob, Duration gracePeriod) {
        if (blob.lastModified().isAfter(clock.instant().minus(gracePeriod))) return false;
        if (!store.tryLockBlob(blob.sha256()) || store.isBlobReferenced(blob.sha256())) return false;
        // Only this module's immutable blob prefix is eligible; media objects are never listed.
        blobs.delete(blob.sha256());
        return true;
    }
}
