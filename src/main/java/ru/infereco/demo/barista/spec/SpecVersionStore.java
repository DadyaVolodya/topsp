package ru.infereco.demo.barista.spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SpecVersionStore {

    private static final Logger LOG = LoggerFactory.getLogger(SpecVersionStore.class);

    private final Path root;
    private final ObjectMapper mapper;
    private final Map<String, List<SpecVersionSnap>> versions = new ConcurrentHashMap<>();
    private final Map<String, List<SpecChange>> changes = new ConcurrentHashMap<>();
    private final Map<String, List<RequirementCodeLink>> links = new ConcurrentHashMap<>();

    public SpecVersionStore() {
        this(Path.of("data/spec-versions"), new ObjectMapper());
    }

    SpecVersionStore(Path root, ObjectMapper mapper) {
        this.root = root;
        this.mapper = mapper.copy().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        loadAll();
    }

    public synchronized SpecVersionSnap save(SpecVersionSnap snap) {
        if (snap == null || snap.documentId() == null) {
            return snap;
        }
        List<SpecVersionSnap> list = versions.computeIfAbsent(snap.documentId(), key -> new ArrayList<>());
        list.removeIf(item -> item.version() == snap.version());
        list.add(snap);
        list.sort(Comparator.comparingInt(SpecVersionSnap::version));
        write(dir(snap.documentId()).resolve("v" + snap.version() + ".json"), snap);
        return snap;
    }

    public synchronized void saveChanges(String documentId, List<SpecChange> items) {
        List<SpecChange> copy = items == null ? List.of() : List.copyOf(items);
        changes.put(documentId, new ArrayList<>(copy));
        write(dir(documentId).resolve("changes.json"), copy);
    }

    public synchronized void saveLinks(String documentId, List<RequirementCodeLink> items) {
        List<RequirementCodeLink> copy = items == null ? List.of() : List.copyOf(items);
        links.put(documentId, new ArrayList<>(copy));
        write(dir(documentId).resolve("links.json"), copy);
    }

    public synchronized void replaceChange(SpecChange change) {
        if (change == null) {
            return;
        }
        List<SpecChange> list = changes.computeIfAbsent(change.documentId(), key -> new ArrayList<>());
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(change.id())) {
                list.set(i, change);
                saveChanges(change.documentId(), list);
                return;
            }
        }
        list.add(change);
        saveChanges(change.documentId(), list);
    }

    public SpecVersionSnap latest(String documentId) {
        List<SpecVersionSnap> list = versionsOf(documentId);
        return list.isEmpty() ? null : list.getLast();
    }

    public synchronized void clearDocument(String documentId) {
        if (documentId == null || documentId.isBlank()) {
            return;
        }
        versions.remove(documentId);
        changes.remove(documentId);
        links.remove(documentId);
        Path dir = dir(documentId);
        try {
            if (Files.isDirectory(dir)) {
                try (var stream = Files.list(dir)) {
                    stream.forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                        }
                    });
                }
            }
        } catch (Exception ex) {
            LOG.warn("не очистил {}: {}", documentId, ex.getMessage());
        }
    }

    public SpecVersionSnap previous(String documentId) {
        List<SpecVersionSnap> list = versionsOf(documentId);
        return list.size() < 2 ? null : list.get(list.size() - 2);
    }

    public List<SpecVersionSnap> lastTwo(String documentId) {
        List<SpecVersionSnap> list = versionsOf(documentId);
        if (list.size() <= 2) {
            return list;
        }
        return List.copyOf(list.subList(list.size() - 2, list.size()));
    }

    public List<SpecVersionSnap> versionsOf(String documentId) {
        List<SpecVersionSnap> list = versions.get(documentId);
        return list == null ? List.of() : List.copyOf(list);
    }

    public List<SpecChange> changesOf(String documentId) {
        List<SpecChange> list = changes.get(documentId);
        return list == null ? List.of() : List.copyOf(list);
    }

    public SpecChange change(String documentId, String changeId) {
        if (changeId == null) {
            return null;
        }
        for (SpecChange change : changesOf(documentId)) {
            if (changeId.equals(change.id())) {
                return change;
            }
        }
        return null;
    }

    public List<RequirementCodeLink> linksOf(String documentId) {
        List<RequirementCodeLink> list = links.get(documentId);
        return list == null ? List.of() : List.copyOf(list);
    }

    public List<String> documentIds() {
        return versions.keySet().stream().sorted().toList();
    }

    private Path dir(String documentId) {
        return root.resolve(documentId == null ? "spec" : documentId);
    }

    private void write(Path file, Object value) {
        try {
            Files.createDirectories(file.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), value);
        } catch (Exception ex) {
            LOG.warn("не записал {}: {}", file, ex.getMessage());
        }
    }

    private void loadAll() {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (var stream = Files.list(root)) {
            stream.filter(Files::isDirectory).forEach(this::loadDocument);
        } catch (Exception ex) {
            LOG.warn("не прочитал версии СП: {}", ex.getMessage());
        }
    }

    private void loadDocument(Path dir) {
        String id = dir.getFileName().toString();
        try (var stream = Files.list(dir)) {
            List<SpecVersionSnap> loaded = new ArrayList<>();
            stream.filter(path -> path.getFileName().toString().startsWith("v") && path.toString().endsWith(".json"))
                    .forEach(path -> {
                        try {
                            loaded.add(mapper.readValue(path.toFile(), SpecVersionSnap.class));
                        } catch (Exception ignored) {
                            // битый снимок пропускаем
                        }
                    });
            loaded.sort(Comparator.comparingInt(SpecVersionSnap::version));
            if (!loaded.isEmpty()) {
                versions.put(id, loaded);
            }
        } catch (Exception ignored) {
            // каталог документа недоступен
        }
        Path changeFile = dir.resolve("changes.json");
        if (Files.isRegularFile(changeFile)) {
            try {
                SpecChange[] items = mapper.readValue(changeFile.toFile(), SpecChange[].class);
                changes.put(id, new ArrayList<>(List.of(items)));
            } catch (Exception ignored) {
                // старый формат
            }
        }
        Path linkFile = dir.resolve("links.json");
        if (Files.isRegularFile(linkFile)) {
            try {
                RequirementCodeLink[] items = mapper.readValue(linkFile.toFile(), RequirementCodeLink[].class);
                links.put(id, new ArrayList<>(List.of(items)));
            } catch (Exception ignored) {
                // старый формат
            }
        }
    }
}
