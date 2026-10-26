package com.jasitha.docreview.audit;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditService {

    public static final String SYSTEM = "system";

    private final AuditEventRepository repository;

    public AuditService(AuditEventRepository repository) {
        this.repository = repository;
    }

    public void record(Long documentId, String actor, String action, String details) {
        repository.save(new AuditEvent(documentId, actor, action, details));
    }

    public List<AuditEvent> history(Long documentId) {
        return repository.findByDocumentIdOrderByOccurredAtAsc(documentId);
    }
}
