package com.aicoding.ai.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class ProjectRagIndexer {

    private final EmbeddingModel embeddingModel;
    private final ProjectCodeChunker chunker;
    private final int embeddingBatchSize;

    public ProjectRagIndexer(
            EmbeddingModel embeddingModel,
            ProjectCodeChunker chunker,
            @Value("${ai.rag.embedding-batch-size:64}") int embeddingBatchSize) {
        this.embeddingModel = embeddingModel;
        this.chunker = chunker;
        this.embeddingBatchSize = Math.max(1, embeddingBatchSize);
    }

    public List<TextSegment> load(Path projectRoot) {
        return chunker.chunkProject(projectRoot);
    }

    public IndexingResult index(Long projectId, List<TextSegment> segments,
                                EmbeddingStore<TextSegment> embeddingStore) {
        Set<String> files = new HashSet<>();
        for (int start = 0; start < segments.size(); start += embeddingBatchSize) {
            int end = Math.min(segments.size(), start + embeddingBatchSize);
            List<TextSegment> batch = segments.subList(start, end);
            for (TextSegment segment : batch) {
                segment.metadata().put("projectId", projectId);
                files.add(segment.metadata().getString(ProjectCodeChunker.FILE_PATH));
            }

            List<Embedding> embeddings = embeddingModel.embedAll(batch).content();
            if (embeddings.size() != batch.size()) {
                throw new IllegalStateException("Embedding count does not match RAG segment count");
            }
            List<String> ids = batch.stream()
                    .map(segment -> embeddingId(projectId, segment))
                    .toList();
            embeddingStore.addAll(ids, embeddings, batch);
        }
        return new IndexingResult(files.size(), segments.size());
    }

    static String embeddingId(Long projectId, TextSegment segment) {
        String identity = projectId + "|"
                + segment.metadata().getString(ProjectCodeChunker.RAG_KEY) + "|"
                + segment.metadata().getString(ProjectCodeChunker.FILE_PATH);
        return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString();
    }

    public record IndexingResult(int files, int chunks) {
    }
}
