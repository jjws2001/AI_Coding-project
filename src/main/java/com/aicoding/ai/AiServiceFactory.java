package com.aicoding.ai;

import com.aicoding.ai.ConcurrentClass.ConcurrentChatModel;
import com.aicoding.ai.tools.CodeAnalysisTool;
import com.aicoding.ai.tools.FileOperationTool;
import com.aicoding.ai.tools.GitOperationTool;
import com.aicoding.ai.tools.SandboxExecutionTool;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
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
    private final StreamingChatModel streamingChatModel;
    private final ConcurrentChatModel concurrentChatModel;

    private final EmbeddingStoreContentRetriever contentRetriever;

    @Value("${ai.chat-memory.max-messages:10}")
    private Integer maxMessages;

    // 内置工具
    private final FileOperationTool fileOperationTool;
    private final CodeAnalysisTool codeAnalysisTool;
    private final GitOperationTool gitOperationTool;
    private final SandboxExecutionTool sandboxExecutionTool;
//    @Resource
//    private McpToolProvider mcpToolProvider;

    // 缓存针对不同项目的 Assistant 代理实例
    private final Map<Long, AiCodingAssistant> project2Assistants = new ConcurrentHashMap<>();

    /**
     * AI编程助手服务:针对 AiServices 动态构建特定项目的智能助手
     */
    public AiCodingAssistant getOrCreateAiAssistantForProject(Long projectId) {
        return project2Assistants.computeIfAbsent(projectId, id ->
                AiServices.builder(AiCodingAssistant.class)
                        .chatModel(chatModel)
                        .streamingChatModel(concurrentChatModel)
                        .contentRetriever(contentRetriever)
                        // 记忆配置
                        .chatMemoryProvider(memoryId ->
                                MessageWindowChatMemory.withMaxMessages(maxMessages))
                        .tools(fileOperationTool)
                        .tools(codeAnalysisTool)
                        .tools(gitOperationTool)
//                        .tools(sandboxExecutionTool)
//                        .toolProvider(mcpToolProvider)
                        .build());
    }
    /**
     * 附加功能：当项目被删除或重新建立索引时，清除缓存
     */
    public void evictAssistant(Long projectId) {
        project2Assistants.remove(projectId);
        log.info("Evicted AI Assistant cache for Project ID: {}", projectId);
    }

    /**
     * 简单对话服务（不带工具和RAG）
     */
    public SimpleChatService simpleChatService() {
        log.info("Building Simple Chat Service");

        return AiServices.builder(SimpleChatService.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .build();
    }
}