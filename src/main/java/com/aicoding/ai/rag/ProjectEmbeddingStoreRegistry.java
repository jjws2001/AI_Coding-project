package com.aicoding.ai.rag;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns one embedding store per project so ingestion and retrieval always use
 * the same physical collection.
 */
@Slf4j
@Component
public class ProjectEmbeddingStoreRegistry {

    private final Map<Long, EmbeddingStore<TextSegment>> stores = new ConcurrentHashMap<>();
    private final List<ProjectEmbeddingStoreProvider> providers;

    public ProjectEmbeddingStoreRegistry(List<ProjectEmbeddingStoreProvider> providers) {
        this.providers = providers;
    }

    @Value("${ai.rag.store:in-memory}")
    private String storeType;

    public EmbeddingStore<TextSegment> get(Long projectId) {
        return stores.computeIfAbsent(projectId, this::createStore);
    }

    public EmbeddingStore<TextSegment> reset(Long projectId) {
        EmbeddingStore<TextSegment> existing = stores.remove(projectId);
        if (existing != null) {
            try {
                existing.removeAll();
            } catch (RuntimeException e) {
                log.warn("Failed to clear embedding store for project {}", projectId, e);
            }
        }
        return get(projectId);
    }

    public void evict(Long projectId) {
        stores.remove(projectId);
    }

    private EmbeddingStore<TextSegment> createStore(Long projectId) {
        String normalizedType = storeType.toLowerCase(Locale.ROOT);
        if ("in-memory".equals(normalizedType) || "memory".equals(normalizedType)) {
            log.info("Creating in-memory embedding store for project {}", projectId);
            return new InMemoryEmbeddingStore<>();
        }
        return providers.stream()
                .filter(provider -> provider.supports(normalizedType))
                .findFirst()
                .map(provider -> provider.create(projectId))
                .orElseThrow(() -> new IllegalStateException(
                        "Embedding store '" + storeType + "' is not available. Build with the matching Maven profile."));
    }
}
