package com.aicoding.ai.rag;

import dev.langchain4j.data.segment.TextSegment;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ProjectSymbolIndex implements AutoCloseable {

    private static final String ORDINAL = "ordinal";
    private static final String FILE_NAME = "fileName";
    private static final String FILE_BASE = "fileBase";
    private static final String QUALIFIED_NAME = "qualifiedName";
    private static final String RAG_KEY = "ragKey";
    private static final String SYMBOL = "symbol";
    private static final String REVERSED_FILE_NAME = "reversedFileName";
    private static final String REVERSED_FILE_BASE = "reversedFileBase";
    private static final String REVERSED_SYMBOL = "reversedSymbol";
    private static final String TERMS = "terms";
    private static final Pattern QUERY_IDENTIFIER = Pattern.compile(
            "[A-Za-z_$][A-Za-z0-9_$.\\-/]*");
    private static final Pattern CODE_IDENTIFIER = Pattern.compile(
            "[A-Za-z_$][A-Za-z0-9_$]*");
    private static final int MAX_QUERY_IDENTIFIERS = 32;
    private static final Set<String> CODE_EXTENSIONS = Set.of(
            "java", "kt", "ts", "tsx", "js", "jsx", "py", "go", "md");

    private final Directory directory;
    private final IndexReader reader;
    private final IndexSearcher searcher;
    private final List<TextSegment> segments;

    private ProjectSymbolIndex(Directory directory, IndexReader reader, List<TextSegment> segments) {
        this.directory = directory;
        this.reader = reader;
        this.searcher = new IndexSearcher(reader);
        this.searcher.setSimilarity(new BM25Similarity());
        this.segments = segments;
    }

    static ProjectSymbolIndex build(List<TextSegment> sourceSegments) {
        Directory directory = new ByteBuffersDirectory();
        List<TextSegment> segments = List.copyOf(sourceSegments);
        try (StandardAnalyzer analyzer = new StandardAnalyzer();
             IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(analyzer))) {
            for (int ordinal = 0; ordinal < segments.size(); ordinal++) {
                writer.addDocument(document(ordinal, segments.get(ordinal)));
            }
            writer.commit();
            return new ProjectSymbolIndex(directory, DirectoryReader.open(directory), segments);
        } catch (IOException | RuntimeException e) {
            try {
                directory.close();
            } catch (IOException ignored) {
            }
            throw new IllegalStateException("Failed to build project symbol index", e);
        }
    }

    List<SearchHit> search(String queryText, int limit) {
        if (segments.isEmpty() || limit <= 0) {
            return List.of();
        }
        Query query = query(queryText);
        if (query instanceof MatchNoDocsQuery) {
            return List.of();
        }
        try {
            TopDocs topDocs = searcher.search(query, limit);
            List<SearchHit> hits = new ArrayList<>(topDocs.scoreDocs.length);
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document document = searcher.storedFields().document(scoreDoc.doc);
                int ordinal = document.getField(ORDINAL).numericValue().intValue();
                hits.add(new SearchHit(segments.get(ordinal), scoreDoc.score));
            }
            return List.copyOf(hits);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to search project symbol index", e);
        }
    }

    private static Document document(int ordinal, TextSegment segment) {
        String fileName = value(segment, ProjectCodeChunker.FILE_NAME);
        String fileBase = withoutExtension(fileName);
        String qualifiedName = value(segment, ProjectCodeChunker.QUALIFIED_NAME);
        String ragKey = value(segment, ProjectCodeChunker.RAG_KEY);
        Set<String> symbols = CodeSymbolExtractor.extract(fileName, segment.text());

        Document document = new Document();
        document.add(new StoredField(ORDINAL, ordinal));
        addExact(document, FILE_NAME, fileName);
        addExact(document, FILE_BASE, fileBase);
        addExact(document, QUALIFIED_NAME, qualifiedName);
        addExact(document, RAG_KEY, ragKey);
        addExact(document, REVERSED_FILE_NAME, reverse(fileName));
        addExact(document, REVERSED_FILE_BASE, reverse(fileBase));
        for (String symbol : symbols) {
            addExact(document, SYMBOL, symbol);
            addExact(document, REVERSED_SYMBOL, reverse(symbol));
        }

        String searchable = String.join(" ", fileName, fileBase, qualifiedName, ragKey,
                String.join(" ", symbols), expandedIdentifiers(segment.text()));
        document.add(new TextField(TERMS, searchable, Field.Store.NO));
        return document;
    }

    private static Query query(String queryText) {
        List<String> identifiers = queryIdentifiers(queryText);
        if (identifiers.isEmpty()) {
            return new MatchNoDocsQuery();
        }

        BooleanQuery.Builder query = new BooleanQuery.Builder();
        Set<String> terms = new LinkedHashSet<>();
        for (String identifier : identifiers) {
            addExactQueries(query, identifier);
            addPartialQueries(query, identifier);
            terms.addAll(identifierParts(identifier));
        }
        for (String term : terms) {
            query.add(new BoostQuery(new TermQuery(new Term(TERMS, term)), 1.2f),
                    BooleanClause.Occur.SHOULD);
        }
        query.setMinimumNumberShouldMatch(1);
        return query.build();
    }

    private static void addExactQueries(BooleanQuery.Builder query, String identifier) {
        query.add(boostedTerm(FILE_NAME, identifier, 14f), BooleanClause.Occur.SHOULD);
        query.add(boostedTerm(FILE_BASE, identifier, 12f), BooleanClause.Occur.SHOULD);
        query.add(boostedTerm(QUALIFIED_NAME, identifier, 14f), BooleanClause.Occur.SHOULD);
        query.add(boostedTerm(RAG_KEY, identifier, 14f), BooleanClause.Occur.SHOULD);
        query.add(boostedTerm(SYMBOL, identifier, 11f), BooleanClause.Occur.SHOULD);
    }

    private static void addPartialQueries(BooleanQuery.Builder query, String identifier) {
        if (identifier.length() < 4) {
            return;
        }
        query.add(new BoostQuery(new PrefixQuery(new Term(FILE_NAME, identifier)), 7f),
                BooleanClause.Occur.SHOULD);
        query.add(new BoostQuery(new PrefixQuery(new Term(FILE_BASE, identifier)), 7f),
                BooleanClause.Occur.SHOULD);
        query.add(new BoostQuery(new PrefixQuery(new Term(SYMBOL, identifier)), 6f),
                BooleanClause.Occur.SHOULD);

        String reversed = reverse(identifier);
        query.add(new BoostQuery(new PrefixQuery(new Term(REVERSED_FILE_NAME, reversed)), 6f),
                BooleanClause.Occur.SHOULD);
        query.add(new BoostQuery(new PrefixQuery(new Term(REVERSED_FILE_BASE, reversed)), 6f),
                BooleanClause.Occur.SHOULD);
        query.add(new BoostQuery(new PrefixQuery(new Term(REVERSED_SYMBOL, reversed)), 5f),
                BooleanClause.Occur.SHOULD);

        int edits = identifier.length() >= 8 ? 2 : 1;
        query.add(new BoostQuery(new FuzzyQuery(new Term(FILE_BASE, identifier), edits, 2), 4f),
                BooleanClause.Occur.SHOULD);
        query.add(new BoostQuery(new FuzzyQuery(new Term(SYMBOL, identifier), edits, 2), 4f),
                BooleanClause.Occur.SHOULD);
    }

    private static BoostQuery boostedTerm(String field, String value, float boost) {
        return new BoostQuery(new TermQuery(new Term(field, value)), boost);
    }

    private static List<String> queryIdentifiers(String queryText) {
        Set<String> identifiers = new LinkedHashSet<>();
        Matcher matcher = QUERY_IDENTIFIER.matcher(queryText == null ? "" : queryText);
        while (matcher.find() && identifiers.size() < MAX_QUERY_IDENTIFIERS) {
            String value = normalize(matcher.group());
            if (value.length() >= 2) {
                identifiers.add(value);
                String pathLeaf = value.substring(value.lastIndexOf('/') + 1);
                int dot = pathLeaf.lastIndexOf('.');
                String suffix = dot >= 0 ? pathLeaf.substring(dot + 1) : "";
                if (CODE_EXTENSIONS.contains(suffix)) {
                    identifiers.add(pathLeaf.substring(0, dot));
                } else if (dot >= 0 && dot < pathLeaf.length() - 1) {
                    identifiers.add(pathLeaf.substring(dot + 1));
                }
            }
        }
        return List.copyOf(identifiers);
    }

    private static String expandedIdentifiers(String text) {
        StringBuilder expanded = new StringBuilder();
        Matcher matcher = CODE_IDENTIFIER.matcher(text);
        while (matcher.find()) {
            for (String part : identifierParts(matcher.group())) {
                expanded.append(part).append(' ');
            }
        }
        return expanded.toString();
    }

    private static Set<String> identifierParts(String identifier) {
        String expanded = identifier
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2")
                .replaceAll("([a-z0-9])([A-Z])", "$1 $2");
        Set<String> parts = new LinkedHashSet<>();
        String original = normalize(identifier);
        if (original.length() >= 2) {
            parts.add(original);
        }
        for (String part : expanded.split("[^A-Za-z0-9]+")) {
            String normalized = normalize(part);
            if (normalized.length() >= 2) {
                parts.add(normalized);
            }
        }
        return parts;
    }

    private static void addExact(Document document, String field, String value) {
        if (value != null && !value.isBlank()) {
            document.add(new StringField(field, normalize(value), Field.Store.NO));
        }
    }

    private static String value(TextSegment segment, String key) {
        String value = segment.metadata().getString(key);
        return value == null ? "" : value;
    }

    private static String withoutExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static String reverse(String value) {
        return new StringBuilder(normalize(value)).reverse().toString();
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    @Override
    public void close() {
        try {
            reader.close();
        } catch (IOException ignored) {
        }
        try {
            directory.close();
        } catch (IOException ignored) {
        }
    }

    record SearchHit(TextSegment segment, float score) {
    }
}
