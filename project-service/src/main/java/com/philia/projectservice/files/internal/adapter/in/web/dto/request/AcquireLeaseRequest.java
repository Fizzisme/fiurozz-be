package com.philia.projectservice.files.internal.adapter.in.web.dto.request;

/** ttlSeconds defaults to 120 when omitted; the application enforces a 30..3600 range. */
public record AcquireLeaseRequest(String holder, String runId, Integer ttlSeconds) {}
