package com.aicoding.ai;

import com.aicoding.Service.ProjectService;
import com.aicoding.ai.guardrail.GuardrailsFilter;
import com.aicoding.ai.harness.HarnessPolicyEngine;
import com.aicoding.ai.harness.HarnessRuntimeRegistry;
import com.aicoding.ai.prompt.DynamicPromptService;
import com.aicoding.ai.rag.ProjectEmbeddingStoreRegistry;
import com.aicoding.ai.rag.ProjectRagIndexer;
import com.aicoding.ai.rag.ProjectSymbolIndexRegistry;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIService {

    private final ProjectRagIndexer ragIndexer;
    private final ProjectEmbeddingStoreRegistry embeddingStoreRegistry;
    private final ProjectSymbolIndexRegistry symbolIndexRegistry;
    private final AiServiceFactory aiServiceFactory;
    private final GuardrailsFilter guardrailsFilter;
    private final ProjectService projectService;
    private final DynamicPromptService promptService;
    private final HarnessPolicyEngine policyEngine;
    private final HarnessRuntimeRegistry runtimeRegistry;

    /** Rebuilds a project-scoped RAG index used by the same project's retriever. */
    @Transactional(readOnly = true)
    public void indexProject(Long projectId) {
        Path projectRoot = projectService.getProjectRootPath(projectId);
        List<TextSegment> segments = ragIndexer.load(projectRoot);
        if (segments.isEmpty()) {
            embeddingStoreRegistry.reset(projectId);
            symbolIndexRegistry.replace(projectId, List.of());
            aiServiceFactory.evictAssistant(projectId);
            log.warn("No indexable documents found for project {}", projectId);
            return;
        }

        EmbeddingStore<TextSegment> projectStore = embeddingStoreRegistry.reset(projectId);
        ProjectRagIndexer.IndexingResult result = ragIndexer.index(projectId, segments, projectStore);
        symbolIndexRegistry.replace(projectId, segments);

        aiServiceFactory.evictAssistant(projectId);
        log.info("Indexed {} files as {} structured chunks for project {}",
                result.files(), result.chunks(), projectId);
    }

    public String chat(Long projectId, String userMessage, String sessionId) {
        String filtered = guardrailsFilter.filter(userMessage);
        HarnessPolicyEngine.WorkflowPolicy policy = policyEngine.classify(filtered);
        String runId = runtimeRegistry.begin(projectId, sessionId, policy);
        try {
            AiCodingAssistant assistant = aiServiceFactory.getOrCreateAiAssistantForProject(projectId);
            String response = assistant.chat(sessionId, promptService.enrichUserMessage(projectId, sessionId, filtered));
            runtimeRegistry.complete(runId, true, "Synchronous response completed");
            return guardrailsFilter.filter(response);
        } catch (RuntimeException e) {
            runtimeRegistry.complete(runId, false, e.getMessage());
            throw e;
        }
    }

    public Flux<String> chatStream(Long projectId, String userMessage, String sessionId) {
        String filtered = guardrailsFilter.filter(userMessage);
        HarnessPolicyEngine.WorkflowPolicy policy = policyEngine.classify(filtered);
        String runId = runtimeRegistry.begin(projectId, sessionId, policy);
        AtomicBoolean completed = new AtomicBoolean(false);

        try {
            AiCodingAssistant assistant = aiServiceFactory.getOrCreateAiAssistantForProject(projectId);
            String enriched = promptService.enrichUserMessage(projectId, sessionId, filtered);
            return assistant.chatStream(sessionId, enriched)
                    .map(guardrailsFilter::filter)
                    .doOnComplete(() -> {
                        completed.set(true);
                        runtimeRegistry.complete(runId, true, "Streaming response completed");
                    })
                    .doOnError(error -> {
                        completed.set(true);
                        runtimeRegistry.complete(runId, false, error.getMessage());
                    })
                    .doOnCancel(() -> {
                        if (completed.compareAndSet(false, true)) {
                            runtimeRegistry.complete(runId, false, "Client cancelled stream");
                        }
                    });
        } catch (RuntimeException e) {
            runtimeRegistry.complete(runId, false, e.getMessage());
            return Flux.error(e);
        }
    }

    public String reviewCode(Long projectId, String code, String sessionId) {
        String request = "Perform a risk-focused code review.\n\n" + guardrailsFilter.filter(code);
        AiCodingAssistant assistant = aiServiceFactory.getOrCreateAiAssistantForProject(projectId);
        return guardrailsFilter.filter(assistant.reviewCode(sessionId,
                promptService.enrichUserMessage(projectId, sessionId, request)));
    }

    public String explainCode(Long projectId, String code, String sessionId) {
        String request = "Explain this code using project context.\n\n" + guardrailsFilter.filter(code);
        AiCodingAssistant assistant = aiServiceFactory.getOrCreateAiAssistantForProject(projectId);
        return guardrailsFilter.filter(assistant.explainCode(sessionId,
                promptService.enrichUserMessage(projectId, sessionId, request)));
    }

}
