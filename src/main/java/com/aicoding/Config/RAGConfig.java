package com.aicoding.Config;

import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * [系统启动阶段]
 * AIConfig  -->  @Bean EmbeddingModel (全局唯一翻译官)
 *                     |
 *                     |--- (注入) --->  AIService (用于写入数据)
 *                     |
 *                     |--- (注入) --->  AiAssistantFactory (用于检索回答)
 *
 * ========================================================================
 *
 * [业务运行阶段]
 * 场景 1：用户上传代码触发 indexProject(101)
 * AIService内部：
 *   1. 动态 `new` 一个指向 project_101 的 EmbeddingStore (用来写)。
 *   2. 调用全局 EmbeddingModel 算向量。
 *   3. 存入 Chroma 物理数据库。
 *
 * 场景 2：用户开始聊天提问 (projectId = 101)
 * AiAssistantFactory内部：
 *   1. 动态 `new` 一个指向 project_101 的 EmbeddingStore (用来读)。
 *   2. 组装 RAG Retriever，放进缓存。
 *   3. AI 收到问题，通过 Retriever 连上 Chroma 物理数据库找答案。
 */

@Configuration
@Slf4j
public class RAGConfig {
    @Value("${ai.openai.api-key}")
    private String openaiApiKey;

    @Value("${ai.chroma.url}")
    private String chromaUrl;

    @Value("${ai.rag.max-results:5}")
    private Integer maxResults;

    @Value("${ai.rag.min-score:0.7}")
    private Double minScore;

    // 存储到Chroma
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(){
        // 纯内存存储，不需要连任何服务器，项目重启数据丢失
        return new InMemoryEmbeddingStore<>();

//        return ChromaEmbeddingStore.builder()
//                .baseUrl(chromaUrl)
//                .collectionName("project_All")
//                .build();
    }

    @Bean
    public DocumentSplitter documentSplitter() {
        // 使用静态方法 DocumentSplitters.recursive()
        // 参数 1: maxSegmentSize (每个切片最大 500 个字符)
        // 参数 2: maxOverlapSize (相邻切片保留 50 个字符的重叠，防止上下文断裂)
        return DocumentSplitters.recursive(500, 50);
    }

    @Bean
    public OpenAiEmbeddingModel embeddingModel(){
        return OpenAiEmbeddingModel.builder()
                .apiKey(openaiApiKey)
                .modelName("text-embedding-3-small")
                .build();
    }

    // 创建嵌入存储RAG检索器
    @Bean
    public EmbeddingStoreContentRetriever contentRetriever(
            EmbeddingStore<TextSegment> embeddingStore,
            OpenAiEmbeddingModel embeddingModel){
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(maxResults)
                .minScore(minScore)
                .build();
    }


}
