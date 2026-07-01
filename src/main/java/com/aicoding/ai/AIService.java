package com.aicoding.ai;

import com.aicoding.Entity.model.Project;
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

    /**
     * Rebuilds a project-scoped RAG index. Ingestion and retrieval share the
     * same store from ProjectEmbeddingStoreRegistry.
     */
    @Transactional(readOnly = true)
    public void indexProject(Long projectId, String... changedFiles) {
        Project project = projectService.getProjectById(projectId);
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
        log.info("Indexed {} documents for project {} ({})", documents.size(), projectId, project.getName());
    }

    public String chatMini(Long projectId, String userMessage, String sessionId) {
        String filteredMessage = guardrailsFilter.filter(userMessage);
        return guardrailsFilter.filter(aiServiceFactory.simpleChatService().chat(sessionId, filteredMessage));
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

    private List<Document> loadProjectDocuments(Path projectRoot) {
        Path normalizedRoot = projectRoot.toAbsolutePath().normalize();
        return FileSystemDocumentLoader.loadDocuments(projectRoot, (PathMatcher) path -> {
            String normalized = path.toString().replace('\\', '/').toLowerCase();
            boolean safePath;
            try {
                safePath = !java.nio.file.Files.isSymbolicLink(path)
                        && path.toAbsolutePath().normalize().startsWith(normalizedRoot)
                        && path.toRealPath().startsWith(normalizedRoot.toRealPath());
            } catch (java.io.IOException e) {
                safePath = false;
            }
            return safePath
                    && !normalized.contains("/node_modules/")
                    && !normalized.contains("/.git/")
                    && !normalized.contains("/target/")
                    && !normalized.contains("/build/")
                    && (normalized.endsWith(".java")
                    || normalized.endsWith(".kt")
                    || normalized.endsWith(".ts")
                    || normalized.endsWith(".tsx")
                    || normalized.endsWith(".js")
                    || normalized.endsWith(".jsx")
                    || normalized.endsWith(".py")
                    || normalized.endsWith(".go")
                    || normalized.endsWith(".md"));
        });
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

    public String generateCode(Long projectId, String requirements, String language) {
        String sessionId = "generation-" + projectId;
        String request = "Generate " + language + " code for these requirements:\n" + guardrailsFilter.filter(requirements);
        AiCodingAssistant assistant = aiServiceFactory.getOrCreateAiAssistantForProject(projectId);
        return guardrailsFilter.filter(assistant.generateCode(sessionId,
                promptService.enrichUserMessage(projectId, sessionId, request)));
    }

    public String fixCode(Long projectId, String code, String errorMessage) {
        String sessionId = "fix-" + projectId;
        String request = "Fix the code using the error evidence.\nError:\n"
                + guardrailsFilter.filter(errorMessage) + "\nCode:\n" + guardrailsFilter.filter(code);
        AiCodingAssistant assistant = aiServiceFactory.getOrCreateAiAssistantForProject(projectId);
        return guardrailsFilter.filter(assistant.fixCode(sessionId,
                promptService.enrichUserMessage(projectId, sessionId, request)));
    }

    public String callOpenClaw(Project project, String task) {
        log.info("Calling OpenClaw for project {} with task: {}", project == null ? null : project.getId(), task);
        return "OpenClaw gateway is not configured";
    }
}
