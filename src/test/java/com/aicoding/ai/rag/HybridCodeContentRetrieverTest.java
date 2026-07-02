package com.aicoding.ai.rag;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HybridCodeContentRetrieverTest {

    private final ProjectSymbolIndexRegistry registry = new ProjectSymbolIndexRegistry();

    @AfterEach
    void closeIndex() {
        registry.evict(1L);
    }

    @Test
    void rrfPromotesSegmentPresentInBothDenseAndSymbolRankings() {
        TextSegment user = segment("UserService.java", "validateAccessToken");
        TextSegment order = segment("OrderService.java", "createOrder");
        registry.replace(1L, List.of(user, order));
        ContentRetriever dense = query -> List.of(Content.from(order), Content.from(user));
        HybridCodeContentRetriever hybrid = new HybridCodeContentRetriever(
                1L, dense, registry, 10, 6, 60, 1.0, 1.2);

        List<Content> results = hybrid.retrieve(Query.from("UserService.java"));

        assertThat(results).extracting(content -> content.textSegment())
                .startsWith(user)
                .contains(order);
        assertThat(results.getFirst().metadata())
                .containsKey(ContentMetadata.RERANKED_SCORE);
    }

    @Test
    void returnsSymbolMatchWhenDenseRetrieverFails() {
        TextSegment user = segment("UserService.java", "validateAccessToken");
        registry.replace(1L, List.of(user));
        ContentRetriever failingDense = query -> {
            throw new IllegalStateException("embedding unavailable");
        };
        HybridCodeContentRetriever hybrid = new HybridCodeContentRetriever(
                1L, failingDense, registry, 10, 6, 60, 1.0, 1.2);

        assertThat(hybrid.retrieve(Query.from("validateAccessToken")))
                .extracting(Content::textSegment)
                .containsExactly(user);
    }

    @Test
    void excludesRuntimeContextFromSymbolQuery() {
        TextSegment user = segment("UserService.java", "validateAccessToken");
        TextSegment order = segment("OrderService.java", "createOrder");
        registry.replace(1L, List.of(user, order));
        HybridCodeContentRetriever hybrid = new HybridCodeContentRetriever(
                1L, query -> List.of(), registry, 10, 6, 60, 1.0, 1.2);

        List<Content> results = hybrid.retrieve(Query.from(
                "UserService.java\n\n<runtime-context>\nOrderService.java\n</runtime-context>"));

        assertThat(results).extracting(Content::textSegment)
                .containsExactly(user);
    }

    @Test
    void lowConfidenceTextOverlapDoesNotOutrankDenseResult() {
        TextSegment lexicalOnly = segmentWithCode("UserService.java", """
                public class UserService {
                    // authentication workflow behavior
                    public void validateAccessToken() {}
                }
                """);
        TextSegment denseWinner = segment("OrderService.java", "createOrder");
        registry.replace(1L, List.of(lexicalOnly, denseWinner));
        HybridCodeContentRetriever hybrid = new HybridCodeContentRetriever(
                1L, query -> List.of(Content.from(denseWinner)),
                registry, 10, 6, 60, 1.0, 1.2);

        assertThat(hybrid.retrieve(Query.from("authentication")))
                .extracting(Content::textSegment)
                .startsWith(denseWinner);
    }

    private TextSegment segment(String fileName, String method) {
        String base = fileName.substring(0, fileName.lastIndexOf('.'));
        return segmentWithCode(fileName, "public class " + base + " {\n"
                + "  public void " + method + "() {}\n}");
    }

    private TextSegment segmentWithCode(String fileName, String code) {
        String base = fileName.substring(0, fileName.lastIndexOf('.'));
        return TextSegment.from(code,
                new Metadata()
                        .put(ProjectCodeChunker.RAG_KEY,
                                "com.example." + base + "|" + fileName)
                        .put(ProjectCodeChunker.QUALIFIED_NAME, "com.example." + base)
                        .put(ProjectCodeChunker.FILE_NAME, fileName)
                        .put(ProjectCodeChunker.FILE_PATH, "src/" + fileName)
                        .put(ProjectCodeChunker.CHUNK_ID, 1));
    }
}
