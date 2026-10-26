package com.jasitha.docreview.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.jasitha.docreview.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Picks the real Claude extractor or the free stub, based on app.ai.mode. */
@Configuration
public class AiConfig {

    @Bean
    @ConditionalOnProperty(name = "app.ai.mode", havingValue = "claude")
    AnthropicClient anthropicClient() {
        // Reads ANTHROPIC_API_KEY from the environment. Never hard-code the key.
        return AnthropicOkHttpClient.fromEnv();
    }

    @Bean
    @ConditionalOnProperty(name = "app.ai.mode", havingValue = "claude")
    AiExtractor claudeAiExtractor(AnthropicClient client, AppProperties properties) {
        return new ClaudeAiExtractor(client, properties.ai().model());
    }

    @Bean
    @ConditionalOnProperty(name = "app.ai.mode", havingValue = "stub", matchIfMissing = true)
    AiExtractor stubAiExtractor() {
        return new StubAiExtractor();
    }
}
