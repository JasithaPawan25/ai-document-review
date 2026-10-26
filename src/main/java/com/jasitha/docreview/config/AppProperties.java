package com.jasitha.docreview.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Storage storage, Ai ai, List<User> users) {

    public record Storage(String uploadDir, List<String> allowedContentTypes) {
    }

    /**
     * @param mode         "stub" (fake AI: free, no API key) or "claude" (real Claude API calls)
     * @param model        Claude model id
     * @param maxTextChars documents longer than this are rejected instead of silently truncated
     */
    public record Ai(String mode, String model, int maxTextChars) {
    }

    /** Demo users for local development only. Replace with a real identity provider / JWT later. */
    public record User(String username, String password, List<String> roles) {
    }
}
