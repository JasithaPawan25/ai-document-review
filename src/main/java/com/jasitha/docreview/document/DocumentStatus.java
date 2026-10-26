package com.jasitha.docreview.document;

/**
 * Lifecycle of a document:
 * UPLOADED -> PROCESSING -> AWAITING_REVIEW -> APPROVED / REJECTED
 * Any processing error moves it to FAILED, from where it can be retried.
 */
public enum DocumentStatus {
    UPLOADED,
    PROCESSING,
    AWAITING_REVIEW,
    APPROVED,
    REJECTED,
    FAILED
}
