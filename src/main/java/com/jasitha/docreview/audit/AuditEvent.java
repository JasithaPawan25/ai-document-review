package com.jasitha.docreview.audit;

import jakarta.persistence.*;

import java.time.Instant;

/** Append-only record of who did what to which document. Never updated or deleted. */
@Entity
@Table(name = "audit_events", indexes = @Index(columnList = "documentId"))
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long documentId;

    @Column(nullable = false)
    private String actor;

    @Column(nullable = false)
    private String action;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(nullable = false)
    private Instant occurredAt;

    protected AuditEvent() {
    }

    public AuditEvent(Long documentId, String actor, String action, String details) {
        this.documentId = documentId;
        this.actor = actor;
        this.action = action;
        this.details = details;
        this.occurredAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getDocumentId() { return documentId; }
    public String getActor() { return actor; }
    public String getAction() { return action; }
    public String getDetails() { return details; }
    public Instant getOccurredAt() { return occurredAt; }
}
