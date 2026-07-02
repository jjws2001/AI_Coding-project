package com.aicoding.ai.rag;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectSymbolIndexRegistryTest {

    private final ProjectSymbolIndexRegistry registry = new ProjectSymbolIndexRegistry();

    @AfterEach
    void closeIndex() {
        registry.evict(1L);
    }

    @Test
    void retrievesByExactFileClassPrefixSuffixTypoAndMethodName() {
        TextSegment userService = segment(
                "com.example.UserService|UserService.java",
                "com.example.UserService",
                "UserService.java",
                "public class UserService {\n"
                        + "  public User validateAccessToken(String token) { return null; }\n"
                        + "}");
        TextSegment orderService = segment(
                "com.example.OrderService|OrderService.java",
                "com.example.OrderService",
                "OrderService.java",
                "public class OrderService {\n"
                        + "  public Order createOrder(Command command) { return null; }\n"
                        + "}");
        registry.replace(1L, List.of(userService, orderService));

        assertFirst("UserService.java", userService);
        assertFirst("com.example.UserService", userService);
        assertFirst("UserServ", userService);
        assertFirst("UserServce", userService);
        assertFirst("validateAccessToken", userService);
        assertFirst("AccessToken", userService);
        assertFirst("validate token", userService);
    }

    @Test
    void atomicallyReplacesPreviousProjectIndex() {
        TextSegment oldSegment = segment("com.example.Old|Old.java",
                "com.example.Old", "Old.java", "class Old {}");
        TextSegment newSegment = segment("com.example.New|New.java",
                "com.example.New", "New.java", "class New {}");
        registry.replace(1L, List.of(oldSegment));
        registry.replace(1L, List.of(newSegment));

        assertThat(registry.search(1L, "Old.java", 5)).isEmpty();
        assertFirst("New.java", newSegment);
    }

    private void assertFirst(String query, TextSegment expected) {
        assertThat(registry.search(1L, query, 5))
                .isNotEmpty()
                .first()
                .extracting(ProjectSymbolIndex.SearchHit::segment)
                .isEqualTo(expected);
    }

    private TextSegment segment(String ragKey, String qualifiedName,
                                String fileName, String code) {
        return TextSegment.from(code, new Metadata()
                .put(ProjectCodeChunker.RAG_KEY, ragKey)
                .put(ProjectCodeChunker.QUALIFIED_NAME, qualifiedName)
                .put(ProjectCodeChunker.FILE_NAME, fileName)
                .put(ProjectCodeChunker.FILE_PATH, "src/" + fileName)
                .put(ProjectCodeChunker.CHUNK_ID, 1));
    }
}
