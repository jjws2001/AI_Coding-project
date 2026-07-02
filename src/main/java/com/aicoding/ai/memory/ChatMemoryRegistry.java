package com.aicoding.ai.memory;

import dev.langchain4j.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatMemoryRegistry {

    private final Map<String, TieredChatMemory> memories = new ConcurrentHashMap<>();
    private final int maxMessages;
    private final int maxToolResultChars;
    private final int maxSummaryChars;

    public ChatMemoryRegistry(
            @Value("${ai.context.max-messages:20}") int maxMessages,
            @Value("${ai.context.max-tool-result-chars:6000}") int maxToolResultChars,
            @Value("${ai.context.max-summary-chars:5000}") int maxSummaryChars) {
        this.maxMessages = maxMessages;
        this.maxToolResultChars = maxToolResultChars;
        this.maxSummaryChars = maxSummaryChars;
    }

    public ChatMemory get(Long projectId, Object memoryId) {
        String key = key(projectId, memoryId);
        return memories.computeIfAbsent(key,
                ignored -> new TieredChatMemory(key, maxMessages, maxToolResultChars, maxSummaryChars));
    }

    public boolean compact(Long projectId, String sessionId) {
        TieredChatMemory memory = memories.get(key(projectId, sessionId));
        if (memory == null) {
            return false;
        }
        memory.compactNow();
        return true;
    }

    public void evictProject(Long projectId) {
        String prefix = projectId + ":";
        memories.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private String key(Long projectId, Object memoryId) {
        return projectId + ":" + String.valueOf(memoryId);
    }
}
