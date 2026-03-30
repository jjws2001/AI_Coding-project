package com.aicoding.ai;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

/**
 * 简单对话服务接口
 */
public interface SimpleChatService {
    String chat(@MemoryId String sessionId,
                @UserMessage String message);

    TokenStream chatStream(
            @MemoryId String sessionId,
            @UserMessage String message);
}
