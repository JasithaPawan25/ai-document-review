package com.jasitha.docreview;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end workflow with the stub AI: upload -> background extraction -> human review. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentWorkflowTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    private static final MockMultipartFile INVOICE = new MockMultipartFile("file", "invoice.txt",
            "text/plain", "Invoice INV-001\nDate: 2026-10-01\nTotal: USD 1,250.00".getBytes());

    @Test
    void uploadIsProcessedThenApprovedByReviewer() throws Exception {
        long id = upload("alice");

        waitForStatus(id, "AWAITING_REVIEW");

        mvc.perform(get("/api/documents/{id}", id).with(user("bob").roles("REVIEWER")))
                .andExpect(jsonPath("$.aiFields[0].value").value("2026-10-01"))
                .andExpect(jsonPath("$.aiFields[1].value").value("USD 1,250.00"));

        mvc.perform(post("/api/documents/{id}/approve", id).with(user("bob").roles("REVIEWER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fields":[{"name":"total_amount","value":"USD 1,250.00","confidence":"high"}],
                                 "comment":"Checked against PO"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.reviewedBy").value("bob"))
                .andExpect(jsonPath("$.reviewedFields[0].name").value("total_amount"));

        mvc.perform(get("/api/documents/{id}/audit", id).with(user("bob").roles("REVIEWER")))
                .andExpect(jsonPath("$[*].action", hasItems("UPLOADED", "AI_EXTRACTION_COMPLETED", "APPROVED")));
    }

    @Test
    void uploaderCannotApprove() throws Exception {
        long id = upload("alice");
        waitForStatus(id, "AWAITING_REVIEW");

        mvc.perform(post("/api/documents/{id}/approve", id).with(user("alice").roles("UPLOADER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void reviewerCannotApproveOwnUpload() throws Exception {
        long id = upload("admin1", "ADMIN");
        waitForStatus(id, "AWAITING_REVIEW");

        mvc.perform(post("/api/documents/{id}/approve", id).with(user("admin1").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploaderCannotSeeOtherUsersDocuments() throws Exception {
        long id = upload("alice");

        mvc.perform(get("/api/documents/{id}", id).with(user("carol").roles("UPLOADER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsUnsupportedFileTypes() throws Exception {
        MockMultipartFile exe = new MockMultipartFile("file", "virus.exe", "application/octet-stream", new byte[]{1});

        mvc.perform(multipart("/api/documents").file(exe).with(user("alice").roles("UPLOADER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresLogin() throws Exception {
        mvc.perform(get("/api/documents")).andExpect(status().isUnauthorized());
    }

    private long upload(String username) throws Exception {
        return upload(username, "UPLOADER");
    }

    private long upload(String username, String role) throws Exception {
        String body = mvc.perform(multipart("/api/documents").file(INVOICE).with(user(username).roles(role)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("id").asLong();
    }

    private void waitForStatus(long id, String expected) throws Exception {
        String last = null;
        for (int i = 0; i < 50; i++) {
            String body = mvc.perform(get("/api/documents/{id}", id).with(user("bob").roles("REVIEWER")))
                    .andReturn().getResponse().getContentAsString();
            JsonNode json = mapper.readTree(body);
            last = json.get("status").asText();
            if (expected.equals(last)) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Expected status " + expected + " but was " + last);
    }
}
