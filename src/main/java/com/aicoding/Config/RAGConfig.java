package com.aicoding.Config;

import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
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
    @Bean
    public DocumentSplitter documentSplitter() {
        // 使用静态方法 DocumentSplitters.recursive()
        // 参数 1: maxSegmentSize (每个切片最大 500 个字符)
        // 参数 2: maxOverlapSize (相邻切片保留 50 个字符的重叠，防止上下文断裂)
        return DocumentSplitters.recursive(500, 50);
    }
}
