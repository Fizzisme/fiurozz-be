package com.philia.projectservice.catalog.internal.application.port.out;

import com.philia.projectservice.catalog.internal.application.exception.MediaStorageUnavailableException;

import java.io.InputStream;
import java.util.Collection;

/**
 * Object storage for project images and videos. Implementations throw
 * {@link MediaStorageUnavailableException} when the storage cannot complete an operation.
 */
public interface ProjectMediaStorage {

    /**
     * Stores the content under the given key. The caller owns and closes the stream.
     */
    void put(String objectKey, String contentType, long sizeBytes, InputStream content);

    /**
     * Removes the given objects; used to compensate uploads when persisting the project fails.
     */
    void deleteAll(Collection<String> objectKeys);

    /**
     * Returns the browser-reachable URL stored in {@code project_media.media_url}.
     */
    String publicUrl(String objectKey);
}
