package com.jasitha.docreview.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * What the AI returns for a document. The Claude SDK turns this record into a
 * JSON schema, so the model's answer is guaranteed to match this shape.
 */
public record ExtractionResult(
        @JsonPropertyDescription("Short document type, e.g. invoice, contract, receipt, letter, other")
        String documentType,

        @JsonPropertyDescription("2-3 sentence plain-English summary of the document")
        String summary,

        @JsonPropertyDescription("Key fields found in the document, such as names, dates, amounts and reference numbers")
        List<ExtractedField> fields
) {

    public record ExtractedField(
            @JsonPropertyDescription("Field name in snake_case, e.g. invoice_number, total_amount, due_date")
            String name,

            @JsonPropertyDescription("Value exactly as written in the document")
            String value,

            @JsonPropertyDescription("How sure you are: high, medium or low")
            String confidence
    ) {
    }
}
