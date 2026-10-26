package com.jasitha.docreview.document;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jasitha.docreview.audit.AuditEvent;
import com.jasitha.docreview.audit.AuditService;
import com.jasitha.docreview.config.AppProperties;
import com.jasitha.docreview.storage.FileStorageService;
import com.jasitha.docreview.web.ApiExceptions.ConflictException;
import com.jasitha.docreview.web.ApiExceptions.ForbiddenException;
import com.jasitha.docreview.web.ApiExceptions.NotFoundException;
import com.jasitha.docreview.web.ApiExceptions.BadRequestException;
import com.jasitha.docreview.web.CurrentUser;
import com.jasitha.docreview.web.DocumentDtos.FieldDto;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
public class DocumentService {

    private final DocumentRepository documents;
    private final FileStorageService storage;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final ObjectMapper objectMapper;
    private final List<String> allowedContentTypes;

    public DocumentService(DocumentRepository documents, FileStorageService storage, AuditService audit,
                           ApplicationEventPublisher events, ObjectMapper objectMapper, AppProperties properties) {
        this.documents = documents;
        this.storage = storage;
        this.audit = audit;
        this.events = events;
        this.objectMapper = objectMapper;
        this.allowedContentTypes = properties.storage().allowedContentTypes();
    }

    @Transactional
    public Document upload(MultipartFile file, CurrentUser user) throws IOException {
        if (file.isEmpty()) {
            throw new BadRequestException("File is empty");
        }
        if (!allowedContentTypes.contains(file.getContentType())) {
            throw new BadRequestException("Unsupported file type: " + file.getContentType()
                    + ". Allowed: " + allowedContentTypes);
        }

        Document doc = new Document();
        doc.setOriginalFilename(file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename());
        doc.setContentType(file.getContentType());
        doc.setStoragePath(storage.store(file));
        doc.setUploadedBy(user.username());
        documents.save(doc);

        audit.record(doc.getId(), user.username(), "UPLOADED", doc.getOriginalFilename());
        // Picked up by DocumentProcessor only after this transaction commits.
        events.publishEvent(new DocumentReadyEvent(doc.getId()));
        return doc;
    }

    @Transactional(readOnly = true)
    public List<Document> list(CurrentUser user) {
        return user.canReview()
                ? documents.findAllByOrderByCreatedAtDesc()
                : documents.findByUploadedByOrderByCreatedAtDesc(user.username());
    }

    @Transactional(readOnly = true)
    public Document get(Long id, CurrentUser user) {
        Document doc = documents.findById(id).orElseThrow(() -> new NotFoundException("Document not found"));
        // Uploaders only see their own documents. Return 404 (not 403) so we don't reveal that it exists.
        if (!user.canReview() && !doc.getUploadedBy().equals(user.username())) {
            throw new NotFoundException("Document not found");
        }
        return doc;
    }

    @Transactional
    public Document approve(Long id, List<FieldDto> correctedFields, String comment, CurrentUser reviewer) {
        Document doc = getForReview(id, reviewer);
        String fieldsJson = correctedFields == null ? doc.getAiFieldsJson() : toJson(correctedFields);
        boolean edited = correctedFields != null;

        doc.setReviewedFieldsJson(fieldsJson);
        doc.setReviewedBy(reviewer.username());
        doc.setReviewComment(comment);
        doc.setStatus(DocumentStatus.APPROVED);
        audit.record(id, reviewer.username(), "APPROVED", edited ? "with corrected fields" : "AI fields accepted");
        return doc;
    }

    @Transactional
    public Document reject(Long id, String comment, CurrentUser reviewer) {
        if (comment == null || comment.isBlank()) {
            throw new BadRequestException("A comment is required when rejecting");
        }
        Document doc = getForReview(id, reviewer);
        doc.setReviewedBy(reviewer.username());
        doc.setReviewComment(comment);
        doc.setStatus(DocumentStatus.REJECTED);
        audit.record(id, reviewer.username(), "REJECTED", comment);
        return doc;
    }

    @Transactional
    public Document retry(Long id, CurrentUser user) {
        Document doc = get(id, user);
        if (doc.getStatus() != DocumentStatus.FAILED) {
            throw new ConflictException("Only FAILED documents can be retried (status is " + doc.getStatus() + ")");
        }
        doc.setStatus(DocumentStatus.UPLOADED);
        audit.record(id, user.username(), "RETRY_REQUESTED", null);
        events.publishEvent(new DocumentReadyEvent(id));
        return doc;
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> history(Long id, CurrentUser user) {
        get(id, user);
        return audit.history(id);
    }

    private Document getForReview(Long id, CurrentUser reviewer) {
        Document doc = get(id, reviewer);
        if (doc.getStatus() != DocumentStatus.AWAITING_REVIEW) {
            throw new ConflictException("Document is not awaiting review (status is " + doc.getStatus() + ")");
        }
        // Separation of duties: nobody approves their own upload.
        if (doc.getUploadedBy().equals(reviewer.username())) {
            throw new ForbiddenException("You cannot review a document you uploaded");
        }
        return doc;
    }

    private String toJson(List<FieldDto> fields) {
        try {
            return objectMapper.writeValueAsString(fields);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Invalid fields");
        }
    }
}
