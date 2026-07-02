package com.aicoding.ai.rag;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MilvusProjectEmbeddingStoreProvider implements ProjectEmbeddingStoreProvider {

    @Value("${ai.milvus.host:localhost}")
    private String host;

    @Value("${ai.milvus.port:19530}")
    private Integer port;

    @Value("${ai.milvus.dimension:1536}")
    private Integer dimension;

    @Override
    public boolean supports(String storeType) {
        return "milvus".equals(storeType);
    }

    @Override
    public EmbeddingStore<TextSegment> create(Long projectId) {
        return MilvusEmbeddingStore.builder()
                .host(host)
                .port(port)
                .collectionName("project_" + projectId)
                .dimension(dimension)
                .indexType(IndexType.HNSW)
                .metricType(MetricType.COSINE)
                .consistencyLevel(ConsistencyLevelEnum.BOUNDED)
                .autoFlushOnInsert(true)
                .build();
    }
}
