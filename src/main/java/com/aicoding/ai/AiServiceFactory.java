package com.aicoding.ai;

import com.aicoding.ai.ConcurrentClass.ConcurrentChatModel;
import com.aicoding.ai.memory.ChatMemoryRegistry;
import com.aicoding.ai.prompt.DynamicPromptService;
import com.aicoding.ai.rag.ProjectEmbeddingStoreRegistry;
import com.aicoding.ai.tools.CodeAnalysisTool;
import com.aicoding.ai.tools.FileOperationTool;
import com.aicoding.ai.tools.GitOperationTool;
import com.aicoding.ai.tools.MemoryTool;
import com.aicoding.ai.tools.SandboxExecutionTool;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiServiceFactory {
    private final ChatModel chatModel;
    private final ConcurrentChatModel concurrentChatModel;
    private final EmbeddingModel embeddingModel;
    private final ProjectEmbeddingStoreRegistry embeddingStoreRegistry;
    private final DynamicPromptService promptService;
    private final ChatMemoryRegistry chatMemoryRegistry;
    private final FileOperationTool fileOperationTool;
    private final CodeAnalysisTool codeAnalysisTool;
    private final GitOperationTool gitOperationTool;
    private final SandboxExecutionTool sandboxExecutionTool;
    private final MemoryTool memoryTool;

    private final Map<Long, AiCodingAssistant> project2Assistants = new ConcurrentHashMap<>();

    @Value("${ai.rag.max-results:5}")
    private Integer maxResults;

    @Value("${ai.rag.min-score:0.7}")
    private Double minScore;

    /** Builds and caches one customized assistant per project. */
    public AiCodingAssistant getOrCreateAiAssistantForProject(Long projectId) {
        return project2Assistants.computeIfAbsent(projectId, id ->
                AiServices.builder(AiCodingAssistant.class)
                        .chatModel(chatModel)
                        .streamingChatModel(concurrentChatModel)
                        .systemMessageProvider(memoryId -> promptService.buildSystemPrompt(id))
                        .contentRetriever(createRetriever(id))
                        .chatMemoryProvider(memoryId -> chatMemoryRegistry.get(id, memoryId))
                        .tools(fileOperationTool)
                        .tools(codeAnalysisTool)
                        .tools(gitOperationTool)
                        .tools(sandboxExecutionTool)
                        .tools(memoryTool)
                        .maxSequentialToolsInvocations(20)
                        .build());
    }

    private EmbeddingStoreContentRetriever createRetriever(Long projectId) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStoreRegistry.get(projectId))
                .embeddingModel(embeddingModel)
                .maxResults(maxResults)
                .minScore(minScore)
                .build();
    }

    /** Evicts stale assistants after an index rebuild or project deletion. */
    public void evictAssistant(Long projectId) {
        project2Assistants.remove(projectId);
        log.info("Evicted AI Assistant cache for project {}", projectId);
    }
}
