package com.jasitha.docreview.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jasitha.docreview.document.DocumentService;
import com.jasitha.docreview.web.DocumentDtos.ApproveRequest;
import com.jasitha.docreview.web.DocumentDtos.AuditResponse;
import com.jasitha.docreview.web.DocumentDtos.DocumentResponse;
import com.jasitha.docreview.web.DocumentDtos.RejectRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService service;
    private final ObjectMapper mapper;

    public DocumentController(DocumentService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    /** Upload returns 202 Accepted: the AI work happens in the background. Poll GET /{id} for the status. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DocumentResponse upload(@RequestParam("file") MultipartFile file,
                                   @AuthenticationPrincipal UserDetails user) throws IOException {
        return toResponse(service.upload(file, CurrentUser.from(user)));
    }

    @GetMapping
    public List<DocumentResponse> list(@AuthenticationPrincipal UserDetails user) {
        return service.list(CurrentUser.from(user)).stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public DocumentResponse get(@PathVariable Long id, @AuthenticationPrincipal UserDetails user) {
        return toResponse(service.get(id, CurrentUser.from(user)));
    }

    @PostMapping("/{id}/approve")
    public DocumentResponse approve(@PathVariable Long id, @Valid @RequestBody(required = false) ApproveRequest body,
                                    @AuthenticationPrincipal UserDetails user) {
        ApproveRequest request = body == null ? new ApproveRequest(null, null) : body;
        return toResponse(service.approve(id, request.fields(), request.comment(), CurrentUser.from(user)));
    }

    @PostMapping("/{id}/reject")
    public DocumentResponse reject(@PathVariable Long id, @Valid @RequestBody RejectRequest body,
                                   @AuthenticationPrincipal UserDetails user) {
        return toResponse(service.reject(id, body.comment(), CurrentUser.from(user)));
    }

    @PostMapping("/{id}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DocumentResponse retry(@PathVariable Long id, @AuthenticationPrincipal UserDetails user) {
        return toResponse(service.retry(id, CurrentUser.from(user)));
    }

    @GetMapping("/{id}/audit")
    public List<AuditResponse> audit(@PathVariable Long id, @AuthenticationPrincipal UserDetails user) {
        return service.history(id, CurrentUser.from(user)).stream().map(AuditResponse::from).toList();
    }

    private DocumentResponse toResponse(com.jasitha.docreview.document.Document doc) {
        return DocumentResponse.from(doc, mapper);
    }
}
