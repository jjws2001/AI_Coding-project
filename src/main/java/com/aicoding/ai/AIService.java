package com.aicoding.ai;

import com.aicoding.Service.ProjectService;
import com.aicoding.ai.guardrail.GuardrailsFilter;
import com.aicoding.ai.harness.HarnessPolicyEngine;
import com.aicoding.ai.harness.HarnessRuntimeRegistry;
import com.aicoding.ai.prompt.DynamicPromptService;
import com.aicoding.ai.rag.ProjectEmbeddingStoreRegistry;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIService {

    private final EmbeddingModel embeddingModel;
    private final DocumentSplitter documentSplitter;
    private final ProjectEmbeddingStoreRegistry embeddingStoreRegistry;
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
        List<Document> documents = loadProjectDocuments(projectRoot);
        if (documents.isEmpty()) {
            log.warn("No indexable documents found for project {}", projectId);
            return;
        }

        EmbeddingStore<TextSegment> projectStore = embeddingStoreRegistry.reset(projectId);
        EmbeddingStoreIngestor.builder()
                .documentSplitter(documentSplitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(projectStore)
                .build()
                .ingest(documents);

        aiServiceFactory.evictAssistant(projectId);
        log.info("Indexed {} documents for project {}", documents.size(), projectId);
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
                    })
                    .onErrorResume(error -> Flux.just(streamErrorMessage(error)));
        } catch (RuntimeException e) {
            runtimeRegistry.complete(runId, false, e.getMessage());
            return Flux.just(streamErrorMessage(e));
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

    private List<Document> loadProjectDocuments(Path projectRoot) {
        Path normalizedRoot = projectRoot.toAbsolutePath().normalize();
        return FileSystemDocumentLoader.loadDocuments(projectRoot, (PathMatcher) path -> {
            String normalized = path.toString().replace('\\', '/').toLowerCase();
            return isSafeIndexPath(path, normalizedRoot)
                    && !normalized.contains("/node_modules/")
                    && !normalized.contains("/.git/")
                    && !normalized.contains("/target/")
                    && !normalized.contains("/build/")
                    && isIndexable(normalized);
        });
    }

    private boolean isSafeIndexPath(Path path, Path normalizedRoot) {
        try {
            return !Files.isSymbolicLink(path)
                    && path.toAbsolutePath().normalize().startsWith(normalizedRoot)
                    && path.toRealPath().startsWith(normalizedRoot.toRealPath());
        } catch (IOException e) {
            return false;
        }
    }

    private boolean isIndexable(String path) {
        return path.endsWith(".java") || path.endsWith(".kt")
                || path.endsWith(".ts") || path.endsWith(".tsx")
                || path.endsWith(".js") || path.endsWith(".jsx")
                || path.endsWith(".py") || path.endsWith(".go")
                || path.endsWith(".md");
    }

    private String streamErrorMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            message = error.getClass().getSimpleName();
        }
        return guardrailsFilter.filter("AI streaming response failed: " + message);
    }
}
