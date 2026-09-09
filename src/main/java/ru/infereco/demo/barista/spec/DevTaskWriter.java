package ru.infereco.demo.barista.spec;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.infereco.demo.barista.config.MeetProperties;

@Component
public class DevTaskWriter {

    private static final Logger LOG = LoggerFactory.getLogger(DevTaskWriter.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final MeetProperties properties;

    public DevTaskWriter(MeetProperties properties) {
        this.properties = properties;
    }

    public Path write(String filename, String change, String excerpt) {
        Path dir = tasksDir();
        try {
            Files.createDirectories(dir);
            String safe = (filename == null ? "sp" : filename).replaceAll("[^\\p{L}\\p{N}._-]+", "-");
            if (safe.toLowerCase(Locale.ROOT).endsWith(".md")) {
                safe = safe.substring(0, safe.length() - 3);
            }
            if (safe.length() > 40) {
                safe = safe.substring(0, 40);
            }
            Path file = dir.resolve(STAMP.format(LocalDateTime.now()) + "-" + safe + ".md");
            String body = """
                    # Задача разработчику: обновить реализацию по СП

                    Исполнитель: разработчик
                    Источник: %s
                    Изменение: %s
                    Когда: %s

                    Сверить aisto-front и сервисы ПТО с новой редакцией системной постановки.
                    Внести правки по изменившимся требованиям и открыть MR.

                    Выдержка:
                    %s
                    """.formatted(
                    filename == null ? "СП" : filename,
                    change == null ? "файл изменён" : change,
                    LocalDateTime.now(),
                    excerpt == null || excerpt.isBlank() ? "нет текста" : excerpt);
            Files.writeString(file, body, StandardCharsets.UTF_8);
            LOG.info("задача разработчику: {}", file);
            return file;
        } catch (Exception ex) {
            LOG.warn("не записал задачу: {}", ex.getMessage());
            return null;
        }
    }

    public Path writeMarkdown(String filename, String markdown) {
        Path dir = tasksDir();
        try {
            Files.createDirectories(dir);
            String safe = (filename == null ? "sp" : filename).replaceAll("[^\\p{L}\\p{N}._-]+", "-");
            if (safe.toLowerCase(Locale.ROOT).endsWith(".md")) {
                safe = safe.substring(0, safe.length() - 3);
            }
            if (safe.length() > 40) {
                safe = safe.substring(0, 40);
            }
            Path file = dir.resolve(STAMP.format(LocalDateTime.now()) + "-" + safe + ".md");
            Files.writeString(file, markdown == null ? "" : markdown, StandardCharsets.UTF_8);
            LOG.info("задача разработчику: {}", file);
            return file;
        } catch (Exception ex) {
            LOG.warn("не записал задачу: {}", ex.getMessage());
            return null;
        }
    }

    public List<TaskView> recent(int limit) {
        Path dir = tasksDir();
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            List<TaskView> items = new ArrayList<>();
            stream.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md"))
                    .sorted(Comparator.comparing(Path::getFileName).reversed())
                    .limit(Math.max(limit, 1))
                    .forEach(path -> {
                        try {
                            String text = Files.readString(path, StandardCharsets.UTF_8);
                            String title = text.lines().findFirst().orElse(path.getFileName().toString());
                            items.add(new TaskView(path.getFileName().toString(), title.replace("#", "").trim(), text));
                        } catch (Exception ignored) {
                            items.add(new TaskView(path.getFileName().toString(), path.getFileName().toString(), ""));
                        }
                    });
            return items;
        } catch (Exception ex) {
            return List.of();
        }
    }

    Path tasksDir() {
        MeetProperties.Specs specs = properties.specs();
        if (specs == null || specs.tasksDir() == null || specs.tasksDir().isBlank()) {
            return Path.of("/Users/sparrow/Documents/pto/задачи/авто");
        }
        return Path.of(specs.tasksDir());
    }

    public record TaskView(String file, String title, String body) {
    }
}
