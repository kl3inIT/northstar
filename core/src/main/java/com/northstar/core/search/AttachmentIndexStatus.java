package com.northstar.core.search;

/** Observable lifecycle of the worker-owned derived index for one attachment. */
public enum AttachmentIndexStatus {
    PENDING,
    PROCESSING,
    READY,
    FAILED,
    UNSUPPORTED
}
