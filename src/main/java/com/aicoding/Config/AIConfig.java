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

    @Value("${ai.openai.model:gpt-4-turbo-preview}")
    private String openaiModel;

    @Value("${ai.openai.base-url}")
    private String openaiBaseUrl;

    @Value("${ai.openai.temperature:0.7}")
    private Double temperature;

    @Value("${ai.openai.max-tokens:2000}")
    private Integer maxTokens;

    @Value("${ai.openai.timeout:60}")
    private Integer timeoutSeconds;

    /**
     * OpenAI 聊天模型（同步）
     */
    @Bean
    public ChatModel openAiChatModel() {
        log.info("Initializing OpenAI Chat Model: {}", openaiModel);

        return OpenAiChatModel.builder()
                .apiKey(openaiApiKey)
                .baseUrl(openaiBaseUrl)
                .modelName(openaiModel)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    /**
     * OpenAI 流式聊天模型
     */
    @Bean
    public StreamingChatModel openAiStreamingChatModel() {
        log.info("Initializing OpenAI Streaming Chat Model: {}", openaiModel);

        return OpenAiStreamingChatModel.builder()
                .apiKey(openaiApiKey)
                .baseUrl(openaiBaseUrl)
                .modelName(openaiModel)
                .temperature(temperature)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    /**
     * OpenAI 嵌入模型
     */
    @Bean
    public EmbeddingModel openAiEmbeddingModel() {
        log.info("Initializing OpenAI Embedding Model");

        return OpenAiEmbeddingModel.builder()
                .apiKey(openaiApiKey)
                .baseUrl(openaiBaseUrl)
                .modelName("text-embedding-3-small")
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }
}
