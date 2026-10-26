package com.jasitha.docreview.document;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jasitha.docreview.ai.AiExtractor;
import com.jasitha.docreview.ai.ExtractionResult;
import com.jasitha.docreview.audit.AuditService;
import com.jasitha.docreview.config.AppProperties;
import com.jasitha.docreview.storage.FileStorageService;
import com.jasitha.docreview.storage.TextExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Background worker: runs after the upload transaction commits, on a separate thread,
 * so the upload request returns immediately while the AI call happens in the background.
 *
 * The slow AI call is deliberately kept outside any database transaction.
 */
@Component
public class DocumentProcessor {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessor.class);

    private final DocumentRepository documents;
    private final FileStorageService storage;
    private final TextExtractor textExtractor;
    private final AiExtractor aiExtractor;
    private final AuditService audit;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;
    private final int maxTextChars;

    public DocumentProcessor(DocumentRepository documents, FileStorageService storage, TextExtractor textExtractor,
                             AiExtractor aiExtractor, AuditService audit, ObjectMapper objectMapper,
                             TransactionTemplate tx, AppProperties properties) {
        this.documents = documents;
        this.storage = storage;
        this.textExtractor = textExtractor;
        this.aiExtractor = aiExtractor;
        this.audit = audit;
        this.objectMapper = objectMapper;
        this.tx = tx;
        this.maxTextChars = properties.ai().maxTextChars();
    }

    @Async
    @TransactionalEventListener
    public void onDocumentReady(DocumentReadyEvent event) {
        Long id = event.documentId();
        Document doc = tx.execute(status -> {
            Document d = documents.findById(id).orElseThrow();
            d.setStatus(DocumentStatus.PROCESSING);
            d.setFailureReason(null);
            audit.record(id, AuditService.SYSTEM, "PROCESSING_STARTED", null);
            return documents.save(d);
        });

        try {
            String text = textExtractor.extract(storage.load(doc.getStoragePath()), doc.getContentType());
            if (text.isBlank()) {
                throw new IllegalStateException("No text found in document (scanned images are not supported yet)");
            }
            if (text.length() > maxTextChars) {
                throw new IllegalStateException("Document is too long (" + text.length() + " characters, limit "
                        + maxTextChars + ")");
            }

            ExtractionResult result = aiExtractor.extract(doc.getOriginalFilename(), text);
            String fieldsJson = toJson(result);

            tx.executeWithoutResult(status -> {
                Document d = documents.findById(id).orElseThrow();
                d.setDocumentType(result.documentType());
                d.setAiSummary(result.summary());
                d.setAiFieldsJson(fieldsJson);
                d.setStatus(DocumentStatus.AWAITING_REVIEW);
                documents.save(d);
                audit.record(id, AuditService.SYSTEM, "AI_EXTRACTION_COMPLETED",
                        (result.fields() == null ? 0 : result.fields().size()) + " fields extracted");
            });
        } catch (Exception e) {
            log.warn("Processing failed for document {}", id, e);
            tx.executeWithoutResult(status -> {
                Document d = documents.findById(id).orElseThrow();
                d.setStatus(DocumentStatus.FAILED);
                d.setFailureReason(e.getMessage());
                documents.save(d);
                audit.record(id, AuditService.SYSTEM, "PROCESSING_FAILED", e.getMessage());
            });
        }
    }

    private String toJson(ExtractionResult result) throws JsonProcessingException {
        return objectMapper.writeValueAsString(result.fields());
    }
}
