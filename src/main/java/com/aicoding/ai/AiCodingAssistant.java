package com.aicoding.ai;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import reactor.core.publisher.Flux;

/** Project-scoped AI coding assistant. */
public interface AiCodingAssistant {

    String chat(@MemoryId String sessionId, @UserMessage String userMessage);

    Flux<String> chatStream(@MemoryId String sessionId, @UserMessage String userMessage);

    String reviewCode(@MemoryId String sessionId, @UserMessage String code);

    String explainCode(@MemoryId String sessionId, @UserMessage String code);
}
