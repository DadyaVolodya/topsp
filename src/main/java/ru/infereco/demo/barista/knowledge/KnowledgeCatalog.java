package ru.infereco.demo.barista.knowledge;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import ru.infereco.demo.barista.metrics.MetricsRegistry;

@Component
public class KnowledgeCatalog {

    private static final Pattern TOKEN = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final int CHUNK_SIZE = 900;
    private static final int PROMPT_BODY_LIMIT = 420;

    private final List<KnowledgeDoc> docs = new CopyOnWriteArrayList<>();
    private final MetricsRegistry metrics;

    public KnowledgeCatalog(MetricsRegistry metrics) {
        this.metrics = metrics;
        docs.addAll(loadSeed());
        metrics.setKnowledgeDocs(docs.size());
    }

    public List<KnowledgeDoc> all() {
        return List.copyOf(docs);
    }

    public List<KnowledgeDoc> retrieve(String query, int topK) {
        Set<String> queryTokens = tokens(query);
        List<KnowledgeDoc> result = new ArrayList<>(docs.stream().filter(d -> "learned".equals(d.source())).toList());
        addRanked(result, docs.stream().filter(d -> "spec".equals(d.source())).toList(), queryTokens, Math.max(topK, 8));
        addRanked(result, docs.stream().filter(d -> "code".equals(d.source())).toList(), queryTokens, Math.max(topK, 3));
        addRanked(result, docs.stream().filter(d -> "upload".equals(d.source())).toList(), queryTokens, Math.max(topK, 2));
        addRanked(result, docs.stream().filter(d -> "seed".equals(d.source())).toList(), queryTokens, Math.max(topK, 2));
        return result;
    }

    public synchronized KnowledgeDoc learn(String title, String body) {
        String normalized = body.trim().toLowerCase(Locale.ROOT);
        for (KnowledgeDoc existing : docs) {
            if (existing.body().trim().toLowerCase(Locale.ROOT).equals(normalized)) {
                return existing;
            }
        }
        return add("learned-" + UUID.randomUUID(), title, body, "learned");
    }

    public synchronized List<KnowledgeDoc> ingestTxt(String filename, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("empty file");
        }
        String name = filename == null || filename.isBlank() ? "notes.txt" : filename;
        if (!name.toLowerCase(Locale.ROOT).endsWith(".txt")) {
            throw new IllegalArgumentException("only .txt is allowed");
        }
        String text = decode(bytes).trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException("txt has no text");
        }
        List<KnowledgeDoc> created = new ArrayList<>();
        List<String> chunks = chunk(text);
        for (int i = 0; i < chunks.size(); i++) {
            String title = chunks.size() == 1 ? name : name + " #" + (i + 1);
            created.add(add("upload-" + UUID.randomUUID(), title, chunks.get(i), "upload"));
        }
        return created;
    }

    public synchronized List<KnowledgeDoc> replaceSpec(String filename, String text) {
        String prefix = filename == null || filename.isBlank() ? "СП" : filename;
        docs.removeIf(doc -> "spec".equals(doc.source()));
        if (text == null || text.isBlank()) {
            metrics.setKnowledgeDocs(docs.size());
            return List.of();
        }
        List<KnowledgeDoc> created = new ArrayList<>();
        List<String> chunks = chunk(text);
        for (int i = 0; i < chunks.size(); i++) {
            String title = chunks.size() == 1 ? prefix : prefix + " #" + (i + 1);
            created.add(add("spec-" + UUID.randomUUID(), title, chunks.get(i), "spec"));
        }
        return created;
    }

    public synchronized List<KnowledgeDoc> replaceCode(String path, String text) {
        String title = path == null || path.isBlank() ? "code" : path;
        docs.removeIf(doc -> "code".equals(doc.source()) && (
                baseTitle(doc.title()).equals(title) || baseTitle(doc.title()).startsWith(title + "#")));
        if (text == null || text.isBlank()) {
            metrics.setKnowledgeDocs(docs.size());
            return List.of();
        }
        return List.of(add("code-" + UUID.randomUUID(), title, text, "code"));
    }

    public synchronized List<KnowledgeDoc> replaceCodeSymbols(String path, List<String> titles, List<String> bodies) {
        String prefix = path == null || path.isBlank() ? "code" : path;
        docs.removeIf(doc -> "code".equals(doc.source()) && (
                baseTitle(doc.title()).equals(prefix) || baseTitle(doc.title()).startsWith(prefix + "#")));
        if (titles == null || bodies == null || titles.isEmpty()) {
            metrics.setKnowledgeDocs(docs.size());
            return List.of();
        }
        List<KnowledgeDoc> created = new ArrayList<>();
        int n = Math.min(titles.size(), bodies.size());
        for (int i = 0; i < n; i++) {
            String body = bodies.get(i);
            if (body == null || body.isBlank()) {
                continue;
            }
            String title = titles.get(i) == null || titles.get(i).isBlank() ? prefix : titles.get(i);
            created.add(add("code-" + UUID.randomUUID(), title, body, "code"));
        }
        return created;
    }

    public List<KnowledgeGroup> groups() {
        Map<String, KnowledgeGroup> grouped = new LinkedHashMap<>();
        for (KnowledgeDoc doc : docs) {
            String title = baseTitle(doc.title());
            String key = doc.source() + "\0" + title;
            KnowledgeGroup current = grouped.get(key);
            if (current == null) {
                grouped.put(key, new KnowledgeGroup(title, doc.source(), 1, doc.body().length()));
            } else {
                grouped.put(key, new KnowledgeGroup(title, current.source(), current.chunks() + 1, current.chars() + doc.body().length()));
            }
        }
        return List.copyOf(grouped.values());
    }

    public static String baseTitle(String title) {
        if (title == null || title.isBlank()) {
            return "";
        }
        String trimmed = title.replaceFirst(" #\\d+$", "");
        int hash = trimmed.lastIndexOf('#');
        if (hash > 0) {
            return trimmed.substring(0, hash);
        }
        return trimmed;
    }

    public long countBySource(String source) {
        return docs.stream().filter(doc -> source.equals(doc.source())).count();
    }

    public String formatForPrompt(List<KnowledgeDoc> retrieved) {
        if (retrieved == null || retrieved.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("Факты из базы знаний:\n");
        for (KnowledgeDoc doc : retrieved) {
            String body = doc.body().replace('\n', ' ');
            if (body.length() > PROMPT_BODY_LIMIT) {
                body = body.substring(0, PROMPT_BODY_LIMIT) + "…";
            }
            sb.append("- [").append(doc.source()).append("] ").append(doc.title()).append(": ")
                    .append(body).append('\n');
        }
        return sb.toString();
    }

    private KnowledgeDoc add(String id, String title, String body, String source) {
        KnowledgeDoc doc = new KnowledgeDoc(id, title, body, source);
        docs.add(doc);
        metrics.setKnowledgeDocs(docs.size());
        return doc;
    }

    private void addRanked(List<KnowledgeDoc> result, List<KnowledgeDoc> candidates, Set<String> queryTokens, int limit) {
        List<KnowledgeDoc> ranked = candidates.stream()
                .sorted(Comparator.comparingInt((KnowledgeDoc d) -> overlap(queryTokens, tokens(d.title() + " " + d.body()))).reversed())
                .limit(limit)
                .toList();
        for (KnowledgeDoc doc : ranked) {
            if (result.stream().noneMatch(existing -> existing.id().equals(doc.id()))) {
                result.add(doc);
            }
        }
    }

    static List<String> chunk(String text) {
        String[] parts = text.split("\\R{2,}");
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String part : parts) {
            String piece = part.trim();
            if (piece.isEmpty()) {
                continue;
            }
            if (current.length() + piece.length() + 1 > CHUNK_SIZE && current.length() > 0) {
                chunks.add(current.toString().trim());
                current.setLength(0);
            }
            if (piece.length() > CHUNK_SIZE) {
                if (current.length() > 0) {
                    chunks.add(current.toString().trim());
                    current.setLength(0);
                }
                for (int i = 0; i < piece.length(); i += CHUNK_SIZE) {
                    chunks.add(piece.substring(i, Math.min(piece.length(), i + CHUNK_SIZE)));
                }
            } else {
                if (current.length() > 0) {
                    current.append('\n');
                }
                current.append(piece);
            }
        }
        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }
        return chunks.isEmpty() ? List.of(text.trim()) : chunks;
    }

    private static String decode(byte[] bytes) {
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        if (utf8.indexOf('\uFFFD') >= 0) {
            return new String(bytes, Charset.forName("windows-1251"));
        }
        return utf8;
    }

    private List<KnowledgeDoc> loadSeed() {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        List<KnowledgeDoc> loaded = new ArrayList<>();
        try {
            Resource[] resources = resolver.getResources("classpath:knowledge/*.md");
            for (Resource resource : resources) {
                String filename = resource.getFilename() == null ? "unknown.md" : resource.getFilename();
                String body = resource.getContentAsString(StandardCharsets.UTF_8);
                String title = filename.replace(".md", "").replace('-', ' ');
                loaded.add(new KnowledgeDoc(filename, title, body, "seed"));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot load knowledge markdown", ex);
        }
        return loaded;
    }

    private static Set<String> tokens(String text) {
        return TOKEN.splitAsStream(text.toLowerCase(Locale.ROOT))
                .filter(token -> token.length() > 2)
                .collect(Collectors.toSet());
    }

    private static int overlap(Set<String> left, Set<String> right) {
        int score = 0;
        for (String token : left) {
            if (right.contains(token)) {
                score++;
            }
        }
        return score;
    }

    public record KnowledgeDoc(String id, String title, String body, String source) {
    }

    public record KnowledgeGroup(String title, String source, int chunks, int chars) {
    }
}
