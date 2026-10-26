package com.jasitha.docreview.document;

/** Published when a document is ready for background AI processing (after upload or retry). */
public record DocumentReadyEvent(Long documentId) {
}
