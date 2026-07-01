package com.aicoding.ai;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import reactor.core.publisher.Flux;

/**
 * AI编程助手接口
 */
public interface AiCodingAssistant {

    /**
     * 同步对话
     */
    String chat(@MemoryId String sessionId, @UserMessage String userMessage);

    /**
     * 流式对话
     */
    Flux<String> chatStream(@MemoryId String sessionId, @UserMessage String userMessage);

    /**
     * 代码审查
     */
    String reviewCode(@MemoryId String sessionId, @UserMessage String code);

    /**
     * 代码解释
     */
    String explainCode(@MemoryId String sessionId, @UserMessage String code);

    /**
     * 生成代码
     */
    String generateCode(@MemoryId String sessionId, @UserMessage String request);

    /**
     * 修复代码错误
     */
    String fixCode(@MemoryId String sessionId, @UserMessage String request);
}
