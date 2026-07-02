package com.aicoding.ai.memory;

import com.aicoding.Entity.model.AgentMemory;
import com.aicoding.Repository.AgentMemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LongTermMemoryService {

    private static final int MAX_MEMORY_CHARS = 8_000;
    private final AgentMemoryRepository repository;

    @Transactional
    public AgentMemory remember(Long projectId, String sessionId, AgentMemory.MemoryType type, String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Memory content must not be blank");
        }
        AgentMemory memory = new AgentMemory();
        memory.setProjectId(Objects.requireNonNull(projectId, "projectId"));
        memory.setSessionId(sessionId);
        memory.setType(type == null ? AgentMemory.MemoryType.PROJECT_FACT : type);
        memory.setContent(limit(content.strip(), MAX_MEMORY_CHARS));
        return repository.save(memory);
    }

    @Transactional(readOnly = true)
    public List<AgentMemory> recall(Long projectId, String query, int limit) {
        try {
            List<AgentMemory> candidates = repository.findTop100ByProjectIdOrderByUpdatedAtDesc(projectId);
            Set<String> terms = tokenize(query);
            return candidates.stream()
                    .sorted(Comparator.comparingInt((AgentMemory memory) -> score(memory.getContent(), terms)).reversed()
                            .thenComparing(AgentMemory::getUpdatedAt, Comparator.reverseOrder()))
                    .filter(memory -> terms.isEmpty() || score(memory.getContent(), terms) > 0)
                    .limit(Math.max(0, limit))
                    .toList();
        } catch (RuntimeException e) {
            log.warn("Long-term memory recall failed for project {}", projectId, e);
            return List.of();
        }
    }

    public String renderRecall(Long projectId, String query, int limit) {
        List<AgentMemory> memories = recall(projectId, query, limit);
        if (memories.isEmpty()) {
            return "";
        }
        return memories.stream()
                .map(memory -> "- [" + memory.getType() + "] " + memory.getContent())
                .collect(Collectors.joining("\n"));
    }

    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        Set<String> terms = new HashSet<>();
        for (String token : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}_-]+")) {
            if (token.length() >= 2) {
                terms.add(token);
            }
        }
        return terms;
    }

    private int score(String content, Set<String> terms) {
        String normalized = content == null ? "" : content.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String term : terms) {
            if (normalized.contains(term)) {
                score++;
            }
        }
        return score;
    }

    private String limit(String value, int maxChars) {
        return value.length() <= maxChars ? value : value.substring(0, maxChars) + "\n[truncated]";
    }
}
