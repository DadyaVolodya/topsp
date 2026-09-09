package ru.infereco.demo.barista.code;

import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.infereco.demo.barista.config.MeetProperties;
import ru.infereco.demo.barista.knowledge.KnowledgeCatalog;

@Component
public class CodeLibrary {

    private static final Logger LOG = LoggerFactory.getLogger(CodeLibrary.class);

    private final MeetProperties properties;
    private final KnowledgeCatalog knowledge;
    private final Map<String, Long> fingerprints = new ConcurrentHashMap<>();
    private final List<CodeChunk> symbols = new CopyOnWriteArrayList<>();
    private volatile Instant lastChange;
    private volatile int files;

    public CodeLibrary(MeetProperties properties, KnowledgeCatalog knowledge) {
        this.properties = properties;
        this.knowledge = knowledge;
    }

    @PostConstruct
    void start() {
        Thread.startVirtualThread(this::loadAllQuiet);
        Thread.startVirtualThread(this::refreshLoop);
    }

    public List<Path> dirs() {
        List<Path> roots = new ArrayList<>();
        MeetProperties.Code code = properties.code();
        if (code != null && code.dirs() != null) {
            for (String dir : code.dirs()) {
                if (dir != null && !dir.isBlank()) {
                    roots.add(resolveDir(dir.trim()));
                }
            }
        }
        boolean anyExists = roots.stream().anyMatch(Files::isDirectory);
        if (!anyExists) {
            roots.clear();
            for (String name : List.of("petclinic-front", "petclinic-back")) {
                Path found = findDemoRepo(name);
                if (found != null) {
                    roots.add(found);
                }
            }
        }
        if (roots.isEmpty()) {
            Path cwd = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
            roots.add(cwd.resolve("demo/code/petclinic-front"));
            roots.add(cwd.resolve("demo/code/petclinic-back"));
        }
        return roots;
    }

    public List<CodeView> list() {
        List<CodeView> items = new ArrayList<>();
        for (Path root : dirs()) {
            items.add(new CodeView(
                    root.getFileName() == null ? root.toString() : root.getFileName().toString(),
                    root.toString(),
                    Files.isDirectory(root)));
        }
        return items;
    }

    /** Превью символов для GET /api/code (фронт рисует список чанков). */
    public List<CodeChunk> preview(int limit) {
        int cap = Math.max(1, Math.min(limit, 100));
        if (symbols.size() <= cap) {
            return List.copyOf(symbols);
        }
        return List.copyOf(symbols.subList(0, cap));
    }

    private static Path resolveDir(String dir) {
        Path raw = Path.of(dir);
        if (Files.isDirectory(raw)) {
            return raw.toAbsolutePath().normalize();
        }
        Path cwd = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        if (!raw.isAbsolute()) {
            Path underCwd = cwd.resolve(raw).normalize();
            if (Files.isDirectory(underCwd)) {
                return underCwd;
            }
        }
        String name = raw.getFileName() == null ? "" : raw.getFileName().toString();
        if (!name.isBlank()) {
            Path found = findDemoRepo(name);
            if (found != null) {
                return found;
            }
        }
        return raw.isAbsolute() ? raw.normalize() : cwd.resolve(raw).normalize();
    }

    private static Path findDemoRepo(String name) {
        Path cwd = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        List<Path> candidates = List.of(
                cwd.resolve("demo/code").resolve(name),
                cwd.resolve("barista-rag/demo/code").resolve(name),
                cwd.getParent() == null ? cwd.resolve(name) : cwd.getParent().resolve("barista-rag/demo/code").resolve(name));
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
        }
        return null;
    }

    public Instant lastChange() {
        return lastChange;
    }

    public int files() {
        return files;
    }

    public long chunks() {
        return knowledge.countBySource("code");
    }

    public int symbols() {
        return symbols.size();
    }

    public List<CodeChunk> search(String query, String side, int limit) {
        Set<String> tokens = tokens(query);
        List<Scored> scored = new ArrayList<>();
        for (CodeChunk chunk : symbols) {
            if (!matchesSide(chunk, side)) {
                continue;
            }
            double score = score(chunk, tokens);
            if (score > 0) {
                scored.add(new Scored(chunk, score));
            }
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        int cap = Math.max(1, limit);
        List<CodeChunk> out = new ArrayList<>();
        for (int i = 0; i < scored.size() && out.size() < cap; i++) {
            out.add(scored.get(i).chunk());
        }
        return out;
    }

    private void refreshLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(120_000);
                loadAllQuiet();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void loadAllQuiet() {
        int loaded = 0;
        List<Path> roots = dirs();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                LOG.warn("корень кода не найден: {}", root);
            }
        }
        for (Path file : CodeIndex.list(roots)) {
            if (reload(rootOf(roots, file), file)) {
                loaded += 1;
            }
        }
        files = loaded;
        lastChange = Instant.now();
        LOG.info("код в RAG: {} файлов, {} кусков", files, chunks());
    }

    private static Path rootOf(List<Path> roots, Path file) {
        Path normalized = file.normalize();
        for (Path root : roots) {
            if (normalized.startsWith(root.normalize())) {
                return root;
            }
        }
        return file.getParent();
    }

    synchronized boolean reload(Path root, Path file) {
        String rel = CodeIndex.relative(root, file);
        String full = CodeIndex.readFull(file);
        long print = full == null ? 0 : full.hashCode() + (long) full.length() * 31;
        Long prev = fingerprints.put(rel, print);
        if (prev != null && prev == print) {
            return full != null && !full.isBlank();
        }
        String repo = root.getFileName() == null ? "" : root.getFileName().toString();
        List<CodeChunk> chunks = CodeChunker.chunk(repo, rel, full);
        symbols.removeIf(item -> rel.equals(item.path()));
        symbols.addAll(chunks);
        List<String> titles = new ArrayList<>();
        List<String> bodies = new ArrayList<>();
        for (CodeChunk chunk : chunks) {
            titles.add(rel + "#" + chunk.symbol());
            bodies.add(chunk.text());
        }
        if (titles.isEmpty()) {
            knowledge.replaceCode(rel, CodeIndex.read(file));
        } else {
            knowledge.replaceCodeSymbols(rel, titles, bodies);
        }
        return full != null && !full.isBlank();
    }

    private static boolean matchesSide(CodeChunk chunk, String side) {
        if (side == null || side.isBlank()) {
            return true;
        }
        String hay = ((chunk.repo() == null ? "" : chunk.repo()) + " " + (chunk.path() == null ? "" : chunk.path()))
                .toLowerCase(Locale.ROOT);
        String want = side.toLowerCase(Locale.ROOT);
        if (want.contains("front")) {
            return hay.contains("front") || hay.contains(".tsx") || hay.contains(".ts") || hay.contains(".jsx");
        }
        if (want.contains("back")) {
            return hay.contains("back") || hay.contains(".java") || hay.contains(".kt");
        }
        return true;
    }

    private static double score(CodeChunk chunk, Set<String> tokens) {
        if (tokens.isEmpty()) {
            return 0.1;
        }
        Set<String> hay = tokens((chunk.symbol() + " " + chunk.path() + " " + chunk.text()));
        int hit = 0;
        for (String token : tokens) {
            if (hay.contains(token)) {
                hit += 1;
            }
        }
        String symbol = chunk.symbol() == null ? "" : chunk.symbol().toLowerCase(Locale.ROOT);
        String path = chunk.path() == null ? "" : chunk.path().toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (symbol.equals(token) || symbol.contains(token) || path.contains(token)) {
                hit += 2;
            }
        }
        if (hit == 0) {
            return 0;
        }
        double boost = 0;
        if (path.contains("visit") || symbol.contains("cancel") || symbol.contains("edit")) {
            boost += 0.3;
        }
        return hit + boost;
    }

    private static Set<String> tokens(String text) {
        Set<String> tokens = new HashSet<>();
        if (text == null) {
            return tokens;
        }
        for (String part : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (part.length() > 2 && !STOP.contains(part)) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    private static final Set<String> STOP = Set.of(
            "the", "and", "for", "with", "this", "that", "from", "описание", "требования",
            "реализации", "макеты", "логирование", "критерии", "приёмки", "приемки",
            "раздел", "пользователь", "системы", "должен", "должна");

    private record Scored(CodeChunk chunk, double score) {
    }

    public record CodeView(String name, String dir, boolean exists) {
    }
}
