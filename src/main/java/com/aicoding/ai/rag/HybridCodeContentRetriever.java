package com.aicoding.ai.rag;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class HybridCodeContentRetriever implements ContentRetriever {

    private static final String RUNTIME_CONTEXT_MARKER = "<runtime-context>";
    private static final double FULL_SYMBOL_CONFIDENCE_SCORE = 8.0;

    private final Long projectId;
    private final ContentRetriever denseRetriever;
    private final ProjectSymbolIndexRegistry symbolIndexRegistry;
    private final int symbolCandidates;
    private final int maxResults;
    private final int rrfK;
    private final double denseWeight;
    private final double symbolWeight;

    public HybridCodeContentRetriever(
            Long projectId,
            ContentRetriever denseRetriever,
            ProjectSymbolIndexRegistry symbolIndexRegistry,
            int symbolCandidates,
            int maxResults,
            int rrfK,
            double denseWeight,
            double symbolWeight) {
        this.projectId = projectId;
        this.denseRetriever = denseRetriever;
        this.symbolIndexRegistry = symbolIndexRegistry;
        this.symbolCandidates = Math.max(1, symbolCandidates);
        this.maxResults = Math.max(1, maxResults);
        this.rrfK = Math.max(1, rrfK);
        this.denseWeight = Math.max(0, denseWeight);
        this.symbolWeight = Math.max(0, symbolWeight);
    }

    @Override
    public List<Content> retrieve(Query query) {
        String userQuery = userQuery(query.text());
        Query focusedQuery = query.metadata() == null
                ? Query.from(userQuery)
                : Query.from(userQuery, query.metadata());
        Map<String, Candidate> candidates = new LinkedHashMap<>();
        RuntimeException denseFailure = null;

        try {
            List<Content> dense = denseRetriever.retrieve(focusedQuery);
            for (int rank = 0; rank < dense.size(); rank++) {
                Content content = dense.get(rank);
                Candidate candidate = candidates.computeIfAbsent(key(content.textSegment()),
                        ignored -> new Candidate(content.textSegment(), content.metadata()));
                candidate.addRrf(denseWeight, rank + 1, rrfK);
            }
        } catch (RuntimeException e) {
            denseFailure = e;
            log.warn("Dense RAG retrieval failed for project {}, falling back to symbols: {}",
                    projectId, e.getMessage());
        }

        List<ProjectSymbolIndex.SearchHit> symbolHits =
                symbolIndexRegistry.search(projectId, userQuery, symbolCandidates);
        for (int rank = 0; rank < symbolHits.size(); rank++) {
            ProjectSymbolIndex.SearchHit hit = symbolHits.get(rank);
            Candidate candidate = candidates.computeIfAbsent(key(hit.segment()),
                    ignored -> new Candidate(hit.segment(), Map.of()));
            double confidence = Math.min(1.0, hit.score() / FULL_SYMBOL_CONFIDENCE_SCORE);
            candidate.addRrf(symbolWeight * confidence, rank + 1, rrfK);
            candidate.symbolScore = Math.max(candidate.symbolScore, hit.score());
        }

        if (candidates.isEmpty() && denseFailure != null) {
            throw denseFailure;
        }

        return candidates.values().stream()
                .sorted(Comparator.comparingDouble(Candidate::fusionScore).reversed()
                        .thenComparing(Comparator.comparingDouble(Candidate::symbolScore).reversed())
                        .thenComparing(candidate -> key(candidate.segment)))
                .limit(maxResults)
                .map(Candidate::content)
                .toList();
    }

    private String userQuery(String query) {
        int marker = query.indexOf(RUNTIME_CONTEXT_MARKER);
        return (marker >= 0 ? query.substring(0, marker) : query).strip();
    }

    private static String key(TextSegment segment) {
        String ragKey = segment.metadata().getString(ProjectCodeChunker.RAG_KEY);
        if (ragKey != null && !ragKey.isBlank()) {
            return ragKey;
        }
        return segment.metadata().getString(ProjectCodeChunker.FILE_PATH) + "|"
                + segment.metadata().getInteger(ProjectCodeChunker.CHUNK_ID) + "|"
                + segment.text().hashCode();
    }

    private static final class Candidate {
        private final TextSegment segment;
        private final Map<ContentMetadata, Object> metadata;
        private double fusionScore;
        private double symbolScore;

        private Candidate(TextSegment segment, Map<ContentMetadata, Object> metadata) {
            this.segment = segment;
            this.metadata = metadata == null ? Map.of() : metadata;
        }

        private void addRrf(double weight, int rank, int rrfK) {
            fusionScore += weight / (rrfK + rank);
        }

        private double fusionScore() {
            return fusionScore;
        }

        private double symbolScore() {
            return symbolScore;
        }

        private Content content() {
            Map<ContentMetadata, Object> resultMetadata = new EnumMap<>(ContentMetadata.class);
            resultMetadata.putAll(metadata);
            resultMetadata.put(ContentMetadata.RERANKED_SCORE, fusionScore);
            return Content.from(segment, resultMetadata);
        }
    }
}
