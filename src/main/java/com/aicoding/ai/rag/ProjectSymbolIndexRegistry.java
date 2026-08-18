package com.aicoding.ai.rag;

import dev.langchain4j.data.segment.TextSegment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@Component
public class ProjectSymbolIndexRegistry {

    private final Map<Long, ProjectSymbolIndex> indexes = new ConcurrentHashMap<>();

    public void replace(Long projectId, List<TextSegment> segments) {
        ProjectSymbolIndex replacement = ProjectSymbolIndex.build(segments);
        indexes.compute(projectId, (ignored, previous) -> {
            close(previous);
            return replacement;
        });
    }

    public void ensure(Long projectId, Supplier<List<TextSegment>> segments) {
        indexes.computeIfAbsent(projectId, ignored -> ProjectSymbolIndex.build(segments.get()));
    }

    public List<ProjectSymbolIndex.SearchHit> search(Long projectId, String query, int limit) {
        AtomicReference<List<ProjectSymbolIndex.SearchHit>> result =
                new AtomicReference<>(List.of());
        indexes.computeIfPresent(projectId, (ignored, index) -> {
            result.set(index.search(query, limit));
            return index;
        });
        return result.get();
    }

    public void evict(Long projectId) {
        indexes.computeIfPresent(projectId, (ignored, index) -> {
            close(index);
            return null;
        });
    }

    private void close(ProjectSymbolIndex index) {
        if (index != null) {
            index.close();
        }
    }
}
