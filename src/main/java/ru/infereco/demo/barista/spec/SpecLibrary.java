package ru.infereco.demo.barista.spec;

import jakarta.annotation.PostConstruct;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.infereco.demo.barista.config.MeetProperties;
import ru.infereco.demo.barista.knowledge.KnowledgeCatalog;
import ru.infereco.demo.barista.knowledge.KnowledgeCatalog.KnowledgeDoc;

@Component
public class SpecLibrary {

    private static final Logger LOG = LoggerFactory.getLogger(SpecLibrary.class);

    private final MeetProperties properties;
    private final KnowledgeCatalog knowledge;
    private final TelegramNotifier telegram;
    private final DevTaskWriter tasks;
    private final SpecPipeline pipeline;
    private final Map<String, Long> fingerprints = new ConcurrentHashMap<>();
    private volatile Instant lastChange;

    public SpecLibrary(
            MeetProperties properties,
            KnowledgeCatalog knowledge,
            TelegramNotifier telegram,
            DevTaskWriter tasks,
            SpecPipeline pipeline
    ) {
        this.properties = properties;
        this.knowledge = knowledge;
        this.telegram = telegram;
        this.tasks = tasks;
        this.pipeline = pipeline;
    }

    @PostConstruct
    void start() {
        Thread.startVirtualThread(this::loadAllQuiet);
        Thread.startVirtualThread(this::watchLoop);
    }

    public Path dir() {
        MeetProperties.Specs specs = properties.specs();
        if (specs == null || specs.dir() == null || specs.dir().isBlank()) {
            return Path.of("/Users/sparrow/Documents/pto/СП");
        }
        return Path.of(specs.dir());
    }

    public List<SpecView> list() {
        List<SpecView> items = new ArrayList<>();
        Path root = dir();
        if (!Files.isDirectory(root)) {
            return items;
        }
        try (Stream<Path> stream = Files.list(root)) {
            stream.filter(this::accepted).forEach(path -> items.add(new SpecView(
                    path.getFileName().toString(),
                    Files.isRegularFile(path) ? fileSize(path) : 0,
                    fingerprints.getOrDefault(path.getFileName().toString(), 0L)
            )));
        } catch (Exception ex) {
            LOG.warn("не прочитал папку СП: {}", ex.getMessage());
        }
        return items;
    }

    public Instant lastChange() {
        return lastChange;
    }

    public long specChunks() {
        return knowledge.countBySource("spec");
    }

    private void loadAllQuiet() {
        Path root = dir();
        if (!Files.isDirectory(root)) {
            LOG.warn("папка СП не найдена: {}", root);
            return;
        }
        try (Stream<Path> stream = Files.list(root)) {
            stream.filter(this::accepted).forEach(path -> reload(path, false));
        } catch (Exception ex) {
            LOG.warn("не загрузил СП: {}", ex.getMessage());
        }
        LOG.info("СП в RAG: {} кусков из {}", specChunks(), root);
    }

    private void watchLoop() {
        Path root = dir();
        if (!Files.isDirectory(root)) {
            return;
        }
        try (WatchService watch = FileSystems.getDefault().newWatchService()) {
            root.register(watch, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
            while (!Thread.currentThread().isInterrupted()) {
                WatchKey key = watch.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    Object name = event.context();
                    if (name == null) {
                        continue;
                    }
                    Path file = root.resolve(name.toString());
                    if (accepted(file)) {
                        Thread.sleep(800);
                        reload(file, true);
                    }
                }
                key.reset();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            LOG.warn("watch СП: {}", ex.getMessage());
        }
    }

    boolean accepted(Path file) {
        if (!SpecText.supported(file)) {
            return false;
        }
        String want = configuredFile();
        if (want == null || want.isBlank()) {
            return true;
        }
        String name = file.getFileName().toString();
        return name.equals(want) || name.equalsIgnoreCase(want);
    }

    private String configuredFile() {
        MeetProperties.Specs specs = properties.specs();
        if (specs == null || specs.file() == null) {
            return "";
        }
        return specs.file().trim();
    }

    synchronized void reload(Path file, boolean notify) {
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        String name = file.getFileName().toString();
        try {
            String text = SpecText.extract(file);
            long print = fingerprint(text);
            Long prev = fingerprints.put(name, print);
            if (prev != null && prev == print) {
                return;
            }
            List<KnowledgeDoc> chunks = knowledge.replaceSpec(name, text);
            lastChange = Instant.now();
            LOG.info("СП {} в RAG, кусков {}", name, chunks.size());
            if (pipeline != null) {
                pipeline.onSpecLoaded(name, text, notify);
            } else if (notify) {
                String excerpt = text.length() > 280 ? text.substring(0, 280) + "…" : text;
                Path task = tasks.write(name, prev == null ? "новая СП" : "СП изменена", excerpt);
                String message = "СП изменилась: " + name
                        + (task == null ? "" : "\nЗадача разработчику: " + task.getFileName())
                        + "\nКусков в RAG: " + chunks.size()
                        + "\n" + excerpt;
                telegram.sendAll(message);
            }
        } catch (Exception ex) {
            LOG.warn("не разобрал СП {}: {}", name, ex.getMessage());
        }
    }

    private static long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (Exception ex) {
            return 0;
        }
    }

    private static long fingerprint(String text) {
        return text == null ? 0 : text.hashCode() + (long) text.length() * 31;
    }

    public record SpecView(String file, long bytes, long fingerprint) {
    }
}
