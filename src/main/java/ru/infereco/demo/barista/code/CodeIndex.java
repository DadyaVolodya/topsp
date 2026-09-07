package ru.infereco.demo.barista.code;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/** Исходники ПТО: фронт aisto-front и бэк aisto-pto-back, без node_modules и сборок. */
public final class CodeIndex {

    private static final Set<String> SKIP_DIR = Set.of(
            "node_modules", ".git", "dist", "build", "target", ".next", "coverage",
            ".idea", "vendor", "out", "tmp", ".gradle", "generated", "bin",
            "generated-sources", "__tests__", "mocks");
    private static final Set<String> EXT = Set.of(".ts", ".tsx", ".js", ".jsx", ".java", ".kt");
    static final int MAX_FILES = 2000;
    static final int MAX_CHARS = 8000;
    static final int MAX_CHARS_FULL = 60_000;
    static final int MAX_BYTES = 180_000;

    private CodeIndex() {
    }

    public static boolean sourceFile(Path path) {
        if (path == null || path.getFileName() == null || !Files.isRegularFile(path)) {
            return false;
        }
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.contains(".test.") || name.contains(".spec.") || name.endsWith(".d.ts")
                || name.endsWith(".min.js") || name.endsWith("test.java") || name.endsWith("tests.java")) {
            return false;
        }
        return EXT.stream().anyMatch(name::endsWith);
    }

    public static boolean skipDir(Path dir) {
        String name = dir == null || dir.getFileName() == null ? "" : dir.getFileName().toString();
        return SKIP_DIR.contains(name);
    }

    public static List<Path> list(List<Path> roots) {
        List<Path> files = new ArrayList<>();
        if (roots == null) {
            return files;
        }
        for (Path root : roots) {
            if (root == null || !Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(path -> Files.isRegularFile(path))
                        .filter(path -> path.normalize().startsWith(root.normalize()))
                        .filter(CodeIndex::notInSkipDir)
                        .filter(CodeIndex::sourceFile)
                        .forEach(files::add);
            } catch (Exception ignored) {
                // пропускаем недоступный корень
            }
        }
        files.sort(Comparator.comparingInt(CodeIndex::priority).thenComparing(Path::toString));
        if (files.size() > MAX_FILES) {
            return List.copyOf(files.subList(0, MAX_FILES));
        }
        return files;
    }

    public static String read(Path file) {
        return read(file, MAX_CHARS);
    }

    public static String readFull(Path file) {
        return read(file, MAX_CHARS_FULL);
    }

    private static String read(Path file, int maxChars) {
        if (!sourceFile(file)) {
            return "";
        }
        try {
            if (Files.size(file) > MAX_BYTES) {
                return "";
            }
            String text = Files.readString(file, StandardCharsets.UTF_8);
            text = text.replace('\u0000', ' ').trim();
            if (text.length() > maxChars) {
                return text.substring(0, maxChars);
            }
            return text;
        } catch (Exception ex) {
            return "";
        }
    }

    public static String relative(Path root, Path file) {
        if (root == null || file == null) {
            return file == null ? "" : file.toString();
        }
        Path repo = root.getFileName() == null ? root : root.getFileName();
        try {
            return repo + "/" + root.relativize(file).toString().replace('\\', '/');
        } catch (Exception ex) {
            return repo + "/" + file.getFileName();
        }
    }

    static int priority(Path path) {
        String name = path.toString().toLowerCase(Locale.ROOT).replace('\\', '/');
        if (name.contains("/src/test/") || name.contains("/bin/") || name.contains("/mocks/")) {
            return 9;
        }
        if (name.contains("app-routes") || name.contains("/rest/controller/") || name.endsWith("/page.tsx")
                || name.contains("-api.ts") || name.contains("securityconfig")) {
            return 0;
        }
        if (name.contains("/app/") || name.contains("/rest/") || name.contains("/lib/")) {
            return 1;
        }
        return 2;
    }

    private static boolean notInSkipDir(Path path) {
        String full = path.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (full.contains("/src/test/") || full.contains("/generated-sources/")) {
            return false;
        }
        for (Path part : path) {
            if (SKIP_DIR.contains(part.toString())) {
                return false;
            }
        }
        return true;
    }
}
