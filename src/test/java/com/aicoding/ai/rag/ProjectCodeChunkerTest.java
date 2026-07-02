package com.aicoding.ai.rag;

import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectCodeChunkerTest {

    private final ProjectCodeChunker chunker = new ProjectCodeChunker(400, 200, 30);

    @TempDir
    Path projectRoot;

    @Test
    void keepsSmallJvmFileAsOneQualifiedSegment() throws IOException {
        Path source = write("src/main/java/com/example/service/UserService.java", List.of(
                "package com.example.service;",
                "public class UserService {}"));

        List<TextSegment> segments = chunker.chunkFile(projectRoot, source);

        assertThat(segments).hasSize(1);
        TextSegment segment = segments.getFirst();
        assertThat(segment.metadata().getString(ProjectCodeChunker.RAG_KEY))
                .isEqualTo("com.example.service.UserService|UserService.java");
        assertThat(segment.metadata().getString(ProjectCodeChunker.QUALIFIED_NAME))
                .isEqualTo("com.example.service.UserService");
        assertThat(segment.metadata().getInteger(ProjectCodeChunker.START_LINE)).isEqualTo(1);
        assertThat(segment.metadata().getInteger(ProjectCodeChunker.END_LINE)).isEqualTo(2);
        assertThat(segment.text()).startsWith(
                "RAG_KEY: com.example.service.UserService|UserService.java\n");
    }

    @Test
    void splitsLargeFileByLinesWithStableOverlap() throws IOException {
        List<String> lines = numberedLines(450);
        lines.set(0, "package com.example.large;");
        Path source = write("src/main/java/com/example/large/LargeService.java", lines);

        List<TextSegment> segments = chunker.chunkFile(projectRoot, source);

        assertThat(segments).hasSize(3);
        assertThat(segments).extracting(segment ->
                        segment.metadata().getString(ProjectCodeChunker.RAG_KEY))
                .containsExactly(
                        "com.example.large.LargeService|LargeService.java|chunk-0001",
                        "com.example.large.LargeService|LargeService.java|chunk-0002",
                        "com.example.large.LargeService|LargeService.java|chunk-0003");
        assertThat(segments).extracting(segment ->
                        segment.metadata().getInteger(ProjectCodeChunker.START_LINE))
                .containsExactly(1, 171, 341);
        assertThat(segments).extracting(segment ->
                        segment.metadata().getInteger(ProjectCodeChunker.END_LINE))
                .containsExactly(200, 370, 450);
    }

    @Test
    void mergesTailSmallerThanHalfChunkIntoPreviousChunk() throws IOException {
        List<String> lines = numberedLines(401);
        lines.set(0, "package com.example.large;");
        Path source = write("LargeService.java", lines);

        List<TextSegment> segments = chunker.chunkFile(projectRoot, source);

        assertThat(segments).hasSize(2);
        assertThat(segments).extracting(segment ->
                        segment.metadata().getInteger(ProjectCodeChunker.START_LINE))
                .containsExactly(1, 171);
        assertThat(segments).extracting(segment ->
                        segment.metadata().getInteger(ProjectCodeChunker.END_LINE))
                .containsExactly(200, 401);
    }

    @Test
    void usesRelativeModulePathForNonJvmFilesAndSkipsGeneratedDirectories() throws IOException {
        write("src/tools/parser.py", List.of("def parse():", "    return True"));
        write("node_modules/package/index.js", List.of("export default 'ignored'"));
        write("target/generated/Generated.java", List.of("class Generated {}"));

        List<TextSegment> segments = chunker.chunkProject(projectRoot);

        assertThat(segments).hasSize(1);
        assertThat(segments.getFirst().metadata().getString(ProjectCodeChunker.RAG_KEY))
                .isEqualTo("src.tools.parser|parser.py");
    }

    private Path write(String relativePath, List<String> lines) throws IOException {
        Path file = projectRoot.resolve(relativePath);
        Files.createDirectories(file.getParent());
        return Files.write(file, lines);
    }

    private List<String> numberedLines(int count) {
        List<String> lines = new ArrayList<>(count);
        for (int line = 1; line <= count; line++) {
            lines.add("// line " + line);
        }
        return lines;
    }
}
