package com.aicoding.ai.tools;

import com.aicoding.Entity.model.AgentMemory;
import com.aicoding.ai.memory.ChatMemoryRegistry;
import com.aicoding.ai.memory.LongTermMemoryService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemoryTool {

    private final LongTermMemoryService memoryService;
    private final ChatMemoryRegistry chatMemoryRegistry;

    @Tool("Persist a stable project fact, decision, user preference, or reusable error lesson. Never store secrets or access tokens.")
    public String remember(
            @P("Project id") Long projectId,
            @P("Current session id") String sessionId,
            @P("One of PROJECT_FACT, USER_PREFERENCE, ERROR_LESSON, DECISION") String type,
            @P("Concise memory without credentials or secrets") String content) {
        try {
            AgentMemory.MemoryType memoryType = AgentMemory.MemoryType.valueOf(type.toUpperCase());
            AgentMemory saved = memoryService.remember(projectId, sessionId, memoryType, content);
            return "Memory stored with id " + saved.getId();
        } catch (RuntimeException e) {
            return "Memory was not stored: " + e.getMessage();
        }
    }

    @Tool("Explicitly compact the current conversation while preserving recent turns and a structured summary.")
    public String compactConversation(
            @P("Project id") Long projectId,
            @P("Current session id") String sessionId) {
        return chatMemoryRegistry.compact(projectId, sessionId)
                ? "Conversation compacted"
                : "No active conversation memory found";
    }
}
