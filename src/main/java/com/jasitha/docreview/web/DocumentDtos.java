package com.jasitha.docreview.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jasitha.docreview.audit.AuditEvent;
import com.jasitha.docreview.document.Document;
import com.jasitha.docreview.document.DocumentStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;

/** Request and response shapes for the REST API. Entities are never returned directly. */
public final class DocumentDtos {

    private DocumentDtos() {
    }

    public record FieldDto(@NotBlank String name, String value, String confidence) {
    }

    /** fields is optional: leave it out to accept the AI's fields as they are. */
    public record ApproveRequest(@Valid List<FieldDto> fields, String comment) {
    }

    public record RejectRequest(@NotBlank String comment) {
    }

    public record DocumentResponse(
            Long id,
            String filename,
            DocumentStatus status,
            String uploadedBy,
            String documentType,
            String aiSummary,
            List<FieldDto> aiFields,
            List<FieldDto> reviewedFields,
            String reviewedBy,
            String reviewComment,
            String failureReason,
            Instant createdAt,
            Instant updatedAt
    ) {
        private static final TypeReference<List<FieldDto>> FIELDS = new TypeReference<>() {
        };

        public static DocumentResponse from(Document d, ObjectMapper mapper) {
            return new DocumentResponse(d.getId(), d.getOriginalFilename(), d.getStatus(), d.getUploadedBy(),
                    d.getDocumentType(), d.getAiSummary(), parse(d.getAiFieldsJson(), mapper),
                    parse(d.getReviewedFieldsJson(), mapper), d.getReviewedBy(), d.getReviewComment(),
                    d.getFailureReason(), d.getCreatedAt(), d.getUpdatedAt());
        }

        private static List<FieldDto> parse(String json, ObjectMapper mapper) {
            if (json == null) {
                return null;
            }
            try {
                return mapper.readValue(json, FIELDS);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    public record AuditResponse(String actor, String action, String details, Instant occurredAt) {

        public static AuditResponse from(AuditEvent e) {
            return new AuditResponse(e.getActor(), e.getAction(), e.getDetails(), e.getOccurredAt());
        }
    }
}
