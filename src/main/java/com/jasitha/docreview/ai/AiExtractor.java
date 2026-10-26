package com.jasitha.docreview.ai;

/** Turns raw document text into a summary and structured fields. */
public interface AiExtractor {

    ExtractionResult extract(String filename, String documentText);
}
