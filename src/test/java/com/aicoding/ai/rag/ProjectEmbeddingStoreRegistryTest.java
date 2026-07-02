package com.aicoding.ai.rag;

import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectEmbeddingStoreRegistryTest {

    @Test
    void isolatesStoresByProjectAndReusesWithinProject() {
        ProjectEmbeddingStoreRegistry registry = new ProjectEmbeddingStoreRegistry(List.of());
        ReflectionTestUtils.setField(registry, "storeType", "in-memory");

        assertThat(registry.get(1L)).isSameAs(registry.get(1L));
        assertThat(registry.get(1L)).isNotSameAs(registry.get(2L));
        assertThat(registry.get(1L)).isInstanceOf(InMemoryEmbeddingStore.class);
    }
}
