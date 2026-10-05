package com.philia.projectservice.files.internal.domain.exception;

import com.philia.projectservice.files.api.FilesResults;

/** Framework-free files error with a stable contract code; no storage secrets reach the response. */
public class FilesException extends RuntimeException {
    private final int status;
    private final String code;
    private final Long currentRevision;
    private final FilesResults.LeaseStatus lease;

    public FilesException(int status, String code, String message) {
        this(status, code, message, null, null);
    }

    public FilesException(int status, String code, String message, Long currentRevision,
                          FilesResults.LeaseStatus lease) {
        super(message);
        this.status = status;
        this.code = code;
        this.currentRevision = currentRevision;
        this.lease = lease;
    }

    public int status() { return status; }
    public String code() { return code; }
    public Long currentRevision() { return currentRevision; }
    public FilesResults.LeaseStatus lease() { return lease; }
}
