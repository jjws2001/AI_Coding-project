package com.aicoding.Config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Slf4j
@Configuration
public class AIConfig {

    @Value("${ai.openai.api-key}")
    private String openaiApiKey;

    @Value("${ai.openai.model:gpt-4.1-mini}")
    private String openaiModel;

    @Value("${ai.openai.base-url}")
    private String openaiBaseUrl;

    @Value("${ai.openai.embedding-api-key:}")
    private String embeddingApiKey;

    @Value("${ai.openai.embedding-base-url:}")
    private String embeddingBaseUrl;

    @Value("${ai.openai.embedding-model:Qwen/Qwen3-VL-Embedding-8B}")
    private String embeddingModel;

    @Value("${ai.openai.embedding-dimensions:4096}")
    private Integer embeddingDimensions;

    @Value("${ai.openai.embedding-timeout:0}")
    private Integer embeddingTimeoutSeconds;

    @Value("${ai.openai.temperature:0.2}")
    private Double temperature;

    @Value("${ai.openai.max-tokens:4000}")
    private Integer maxTokens;

    @Value("${ai.openai.timeout:60}")
    private Integer timeoutSeconds;

    @Value("${ai.openai.log-requests:false}")
    private boolean logRequests;

    @Value("${ai.openai.log-responses:false}")
    private boolean logResponses;

    @Bean
    public ChatModel openAiChatModel() {
        log.info("Initializing chat model {}", openaiModel);
        return OpenAiChatModel.builder()
                .apiKey(openaiApiKey)
                .baseUrl(openaiBaseUrl)
                .modelName(openaiModel)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
    }

    @Bean
    public StreamingChatModel openAiStreamingChatModel() {
        log.info("Initializing streaming chat model {}", openaiModel);
        return OpenAiStreamingChatModel.builder()
                .apiKey(openaiApiKey)
                .baseUrl(openaiBaseUrl)
                .modelName(openaiModel)
                .temperature(temperature)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
    }

    @Bean
    public EmbeddingModel openAiEmbeddingModel() {
        log.info("Initializing embedding model {} with {} dimensions", embeddingModel, embeddingDimensions);
        return OpenAiEmbeddingModel.builder()
                .apiKey(firstNonBlank(embeddingApiKey, openaiApiKey))
                .baseUrl(firstNonBlank(embeddingBaseUrl, openaiBaseUrl))
                .modelName(embeddingModel)
                .dimensions(embeddingDimensions)
                .timeout(Duration.ofSeconds(positiveOrFallback(embeddingTimeoutSeconds, timeoutSeconds)))
                .build();
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private int positiveOrFallback(Integer value, Integer fallback) {
        return value != null && value > 0 ? value : fallback;
    }
}
