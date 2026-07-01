package com.aicoding.ai.rag;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;

public interface ProjectEmbeddingStoreProvider {
    boolean supports(String storeType);

    EmbeddingStore<TextSegment> create(Long projectId);
}
