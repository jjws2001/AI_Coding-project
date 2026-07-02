package com.aicoding.ai.rag;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
@Component
public class ProjectCodeChunker {

    public static final String RAG_KEY = "ragKey";
    public static final String QUALIFIED_NAME = "qualifiedName";
    public static final String FILE_NAME = "fileName";
    public static final String FILE_PATH = "filePath";
    public static final String CHUNK_ID = "chunkId";
    public static final String START_LINE = "startLine";
    public static final String END_LINE = "endLine";
    public static final String TOTAL_LINES = "totalLines";
    public static final String LARGE_FILE = "largeFile";

    private static final Pattern JVM_PACKAGE = Pattern.compile(
            "^\\s*package\\s+([A-Za-z_][\\w]*(?:\\.[A-Za-z_][\\w]*)*)\\s*;?\\s*$");
    private static final Set<String> IGNORED_DIRECTORIES = Set.of(
            ".git", ".idea", "node_modules", "target", "build", "dist", "out");
    private static final Set<String> INDEXABLE_EXTENSIONS = Set.of(
            "java", "kt", "ts", "tsx", "js", "jsx", "py", "go", "md");

    private final int largeFileLines;
    private final int chunkSizeLines;
    private final int chunkOverlapLines;

    public ProjectCodeChunker(
            @Value("${ai.rag.large-file-lines:400}") int largeFileLines,
            @Value("${ai.rag.chunk-size-lines:200}") int chunkSizeLines,
            @Value("${ai.rag.chunk-overlap-lines:30}") int chunkOverlapLines) {
        this.chunkSizeLines = Math.max(50, chunkSizeLines);
        this.chunkOverlapLines = Math.max(0, Math.min(chunkOverlapLines, this.chunkSizeLines - 1));
        this.largeFileLines = Math.max(this.chunkSizeLines, largeFileLines);
    }

    public List<TextSegment> chunkProject(Path projectRoot) {
        Path normalizedRoot = projectRoot.toAbsolutePath().normalize();
        Path realRoot;
        try {
            realRoot = normalizedRoot.toRealPath();
        } catch (IOException e) {
            throw new IllegalStateException("Project root is unavailable for RAG indexing: " + projectRoot, e);
        }

        List<Path> files;
        try (Stream<Path> paths = Files.walk(normalizedRoot)) {
            files = paths
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> isIndexable(normalizedRoot, path))
                    .filter(path -> isSafe(path, normalizedRoot, realRoot))
                    .sorted(Comparator.comparing(path -> normalize(normalizedRoot.relativize(path))))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan project for RAG indexing", e);
        }

        List<TextSegment> segments = new ArrayList<>();
        for (Path file : files) {
            try {
                segments.addAll(chunkFile(normalizedRoot, file));
            } catch (IOException | RuntimeException e) {
                log.warn("Skipping unreadable RAG source {}: {}", file, e.getMessage());
            }
        }
        return List.copyOf(segments);
    }

    public List<TextSegment> chunkFile(Path projectRoot, Path file) throws IOException {
        Path normalizedRoot = projectRoot.toAbsolutePath().normalize();
        Path normalizedFile = file.toAbsolutePath().normalize();
        if (!normalizedFile.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("RAG source is outside the project root: " + file);
        }

        List<String> lines = Files.readAllLines(normalizedFile, StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            return List.of();
        }

        String relativePath = normalize(normalizedRoot.relativize(normalizedFile));
        String fileName = normalizedFile.getFileName().toString();
        String qualifiedName = qualifiedName(relativePath, fileName, lines);
        boolean large = lines.size() > largeFileLines;
        List<LineRange> ranges = large ? lineRanges(lines.size()) : List.of(new LineRange(0, lines.size()));

        List<TextSegment> segments = new ArrayList<>(ranges.size());
        for (int index = 0; index < ranges.size(); index++) {
            LineRange range = ranges.get(index);
            int chunkId = index + 1;
            String ragKey = ragKey(qualifiedName, fileName, large, chunkId);
            Metadata metadata = new Metadata()
                    .put(RAG_KEY, ragKey)
                    .put(QUALIFIED_NAME, qualifiedName)
                    .put(FILE_NAME, fileName)
                    .put(FILE_PATH, relativePath)
                    .put(CHUNK_ID, chunkId)
                    .put(START_LINE, range.startInclusive() + 1)
                    .put(END_LINE, range.endExclusive())
                    .put(TOTAL_LINES, lines.size())
                    .put(LARGE_FILE, String.valueOf(large));
            String code = String.join("\n", lines.subList(range.startInclusive(), range.endExclusive()));
            segments.add(TextSegment.from(enrichedText(ragKey, qualifiedName, fileName,
                    relativePath, chunkId, range, lines.size(), code), metadata));
        }
        return List.copyOf(segments);
    }

    private List<LineRange> lineRanges(int totalLines) {
        int step = chunkSizeLines - chunkOverlapLines;
        List<LineRange> ranges = new ArrayList<>();
        for (int start = 0; start < totalLines; start += step) {
            int end = Math.min(totalLines, start + chunkSizeLines);
            ranges.add(new LineRange(start, end));
            if (end == totalLines) {
                break;
            }
        }

        int minimumTail = Math.max(1, chunkSizeLines / 2);
        if (ranges.size() > 1) {
            LineRange last = ranges.get(ranges.size() - 1);
            if (last.size() < minimumTail) {
                int previousIndex = ranges.size() - 2;
                LineRange previous = ranges.get(previousIndex);
                ranges.set(previousIndex, new LineRange(previous.startInclusive(), totalLines));
                ranges.remove(ranges.size() - 1);
            }
        }
        return ranges;
    }

    private String qualifiedName(String relativePath, String fileName, List<String> lines) {
        String extension = extension(fileName);
        String simpleName = withoutExtension(fileName);
        if ("java".equals(extension) || "kt".equals(extension)) {
            for (String line : lines) {
                Matcher matcher = JVM_PACKAGE.matcher(line.replace("\uFEFF", ""));
                if (matcher.matches()) {
                    return matcher.group(1) + "." + simpleName;
                }
            }
            return simpleName;
        }

        String modulePath = withoutExtension(relativePath).replace('/', '.');
        return modulePath.isBlank() ? simpleName : modulePath;
    }

    private String ragKey(String qualifiedName, String fileName, boolean large, int chunkId) {
        String base = qualifiedName + "|" + fileName;
        return large ? base + "|chunk-" + String.format(Locale.ROOT, "%04d", chunkId) : base;
    }

    private String enrichedText(String ragKey, String qualifiedName, String fileName, String filePath,
                                int chunkId, LineRange range, int totalLines, String code) {
        return "RAG_KEY: " + ragKey + '\n'
                + "QUALIFIED_NAME: " + qualifiedName + '\n'
                + "FILE_NAME: " + fileName + '\n'
                + "FILE_PATH: " + filePath + '\n'
                + "CHUNK_ID: " + chunkId + '\n'
                + "LINES: " + (range.startInclusive() + 1) + "-" + range.endExclusive()
                + " of " + totalLines + '\n'
                + "CODE:\n" + code;
    }

    private boolean isIndexable(Path root, Path path) {
        Path relative = root.relativize(path);
        for (Path part : relative) {
            if (IGNORED_DIRECTORIES.contains(part.toString().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return INDEXABLE_EXTENSIONS.contains(extension(path.getFileName().toString()));
    }

    private boolean isSafe(Path path, Path normalizedRoot, Path realRoot) {
        try {
            return !Files.isSymbolicLink(path)
                    && path.toAbsolutePath().normalize().startsWith(normalizedRoot)
                    && path.toRealPath().startsWith(realRoot);
        } catch (IOException e) {
            return false;
        }
    }

    private String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String withoutExtension(String value) {
        int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        int dot = value.lastIndexOf('.');
        return dot > slash ? value.substring(0, dot) : value;
    }

    private String normalize(Path path) {
        return path.toString().replace('\\', '/');
    }

    private record LineRange(int startInclusive, int endExclusive) {
        private int size() {
            return endExclusive - startInclusive;
        }
    }
}
