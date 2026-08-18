package com.aicoding.ai.rag;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectRagIndexerTest {

    @Test
    void derivesDeterministicProjectScopedMilvusIdFromStructuredIdentity() {
        TextSegment segment = TextSegment.from("code", new Metadata()
                .put(ProjectCodeChunker.RAG_KEY,
                        "com.example.UserService|UserService.java|chunk-0001")
                .put(ProjectCodeChunker.FILE_PATH,
                        "src/main/java/com/example/UserService.java"));

        String first = ProjectRagIndexer.embeddingId(7L, segment);
        String second = ProjectRagIndexer.embeddingId(7L, segment);
        String anotherProject = ProjectRagIndexer.embeddingId(8L, segment);

        assertThat(first).isEqualTo(second).hasSize(36);
        assertThat(anotherProject).isNotEqualTo(first);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void embedsAndStoresStructuredSegmentsWithCustomIds() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        EmbeddingStore<TextSegment> embeddingStore = mock(EmbeddingStore.class);
        ProjectRagIndexer indexer = new ProjectRagIndexer(
                embeddingModel, new ProjectCodeChunker(400, 200, 30), 64);
        TextSegment segment = TextSegment.from("code", new Metadata()
                .put(ProjectCodeChunker.RAG_KEY, "com.example.UserService|UserService.java")
                .put(ProjectCodeChunker.FILE_PATH,
                        "src/main/java/com/example/UserService.java"));
        List<TextSegment> segments = List.of(segment);
        when(embeddingModel.embedAll(anyList())).thenReturn(
                Response.from(List.of(Embedding.from(new float[]{0.2f, 0.8f}))));

        ProjectRagIndexer.IndexingResult result = indexer.index(7L, segments, embeddingStore);

        ArgumentCaptor<List<String>> ids = ArgumentCaptor.forClass((Class) List.class);
        verify(embeddingStore).addAll(ids.capture(), anyList(), eq(segments));
        assertThat(ids.getValue()).containsExactly(ProjectRagIndexer.embeddingId(7L, segment));
        assertThat(segment.metadata().getLong("projectId")).isEqualTo(7L);
        assertThat(result).isEqualTo(new ProjectRagIndexer.IndexingResult(1, 1));
    }
}
