package ru.infereco.demo.barista.code;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Эвристика: class / method / export / component, не один обрезанный файл. */
public final class CodeChunker {

    private static final Pattern JAVA_TYPE = Pattern.compile(
            "(?m)^[ \\t]*(?:(?:public|protected|private|static|final|abstract|sealed|non-sealed)\\s+)*"
                    + "(class|interface|enum|record)\\s+(\\w+)");
    private static final Pattern JAVA_METHOD = Pattern.compile(
            "(?m)^[ \\t]*(?:(?:public|protected|private|static|final|synchronized|native|default|abstract)\\s+)+"
                    + "(?:[\\w.<>,\\[\\]?\\s]+)\\s+(\\w+)\\s*\\(");
    private static final Pattern TS_DECL = Pattern.compile(
            "(?m)^[ \\t]*export\\s+(?:default\\s+)?(?:async\\s+)?"
                    + "(?:function|class|const|let)\\s+(\\w+)"
                    + "|(?m)^[ \\t]*(?:export\\s+)?(?:async\\s+)?function\\s+(\\w+)"
                    + "|(?m)^[ \\t]*(?:export\\s+)?const\\s+(\\w+)\\s*=\\s*(?:async\\s*)?(?:\\(|[A-Z]|function)");
    private static final int MAX_CHUNK = 2800;

    private CodeChunker() {
    }

    public static List<CodeChunk> chunk(String repo, String path, String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String language = languageOf(path);
        List<Hit> hits = hits(text, language);
        if (hits.isEmpty()) {
            return List.of(fileChunk(repo, path, language, text));
        }
        hits.sort(Comparator.comparingInt(Hit::start));
        List<CodeChunk> chunks = new ArrayList<>();
        for (int i = 0; i < hits.size(); i++) {
            Hit hit = hits.get(i);
            int end = i + 1 < hits.size() ? hits.get(i + 1).start() : text.length();
            int from = Math.max(0, hit.start());
            if (i > 0) {
                from = Math.max(0, from - overlap(text, from));
            }
            String body = text.substring(from, end).trim();
            if (body.length() > MAX_CHUNK) {
                body = body.substring(0, MAX_CHUNK);
            }
            if (body.length() < 12) {
                continue;
            }
            int startLine = lineOf(text, from);
            int endLine = lineOf(text, Math.min(end, text.length()) - 1);
            chunks.add(new CodeChunk(
                    repo == null ? repoOf(path) : repo,
                    path,
                    language,
                    hit.symbol(),
                    symbolType(path, language, hit.kind(), hit.symbol()),
                    startLine,
                    Math.max(startLine, endLine),
                    body));
        }
        if (chunks.isEmpty()) {
            return List.of(fileChunk(repo, path, language, text));
        }
        return chunks;
    }

    private static List<Hit> hits(String text, String language) {
        List<Hit> hits = new ArrayList<>();
        if ("java".equals(language) || "kt".equals(language)) {
            collect(JAVA_TYPE, text, 2, hits, "class");
            collect(JAVA_METHOD, text, 1, hits, "method");
        } else {
            Matcher matcher = TS_DECL.matcher(text);
            while (matcher.find()) {
                String symbol = firstGroup(matcher);
                if (symbol == null || skipJsName(symbol)) {
                    continue;
                }
                hits.add(new Hit(matcher.start(), symbol, "function"));
            }
        }
        return hits;
    }

    private static void collect(Pattern pattern, String text, int group, List<Hit> hits, String kind) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String symbol = matcher.group(group);
            if (symbol == null || skipJavaName(symbol)) {
                continue;
            }
            hits.add(new Hit(matcher.start(), symbol, kind));
        }
    }

    private static CodeChunk fileChunk(String repo, String path, String language, String text) {
        String body = text.length() > MAX_CHUNK ? text.substring(0, MAX_CHUNK) : text;
        String name = path == null ? "file" : path.substring(path.lastIndexOf('/') + 1);
        return new CodeChunk(
                repo == null ? repoOf(path) : repo,
                path,
                language,
                name,
                "file",
                1,
                lineOf(text, text.length() - 1),
                body);
    }

    private static int overlap(String text, int from) {
        int lines = 0;
        int i = from - 1;
        while (i >= 0 && lines < 3) {
            if (text.charAt(i) == '\n') {
                lines += 1;
            }
            i -= 1;
        }
        return from - Math.max(0, i + 1);
    }

    static String languageOf(String path) {
        String name = path == null ? "" : path.toLowerCase(Locale.ROOT);
        if (name.endsWith(".java")) {
            return "java";
        }
        if (name.endsWith(".kt")) {
            return "kt";
        }
        if (name.endsWith(".tsx")) {
            return "tsx";
        }
        if (name.endsWith(".jsx")) {
            return "jsx";
        }
        if (name.endsWith(".js")) {
            return "js";
        }
        return "ts";
    }

    static String repoOf(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        int slash = path.indexOf('/');
        return slash < 0 ? path : path.substring(0, slash);
    }

    static String symbolType(String path, String language, String kind, String symbol) {
        String hay = (path == null ? "" : path).toLowerCase(Locale.ROOT);
        if (hay.contains("app-routes") || hay.endsWith("/page.tsx") || hay.contains("-api.ts")) {
            return hay.contains("app-routes") || hay.endsWith("/page.tsx") ? "route" : "api";
        }
        if (symbol != null) {
            if (symbol.startsWith("use") && symbol.length() > 3 && Character.isUpperCase(symbol.charAt(3))) {
                return "hook";
            }
            if (symbol.endsWith("Controller")) {
                return "controller";
            }
            if (symbol.endsWith("Service")) {
                return "service";
            }
            if (symbol.endsWith("Repository")) {
                return "repository";
            }
            if (symbol.endsWith("Dto") || symbol.endsWith("DTO") || symbol.endsWith("Request") || symbol.endsWith("Response")) {
                return "dto";
            }
            if (("tsx".equals(language) || "jsx".equals(language))
                    && !symbol.isEmpty()
                    && Character.isUpperCase(symbol.charAt(0))) {
                return "component";
            }
        }
        return kind == null ? "function" : kind;
    }

    private static int lineOf(String text, int index) {
        if (text == null || text.isEmpty() || index < 0) {
            return 1;
        }
        int line = 1;
        int limit = Math.min(index, text.length() - 1);
        for (int i = 0; i <= limit; i++) {
            if (text.charAt(i) == '\n') {
                line += 1;
            }
        }
        return line;
    }

    private static String firstGroup(Matcher matcher) {
        for (int i = 1; i <= matcher.groupCount(); i++) {
            String value = matcher.group(i);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static boolean skipJavaName(String name) {
        return name == null
                || name.equals("if")
                || name.equals("for")
                || name.equals("while")
                || name.equals("switch")
                || name.equals("catch")
                || name.equals("new")
                || name.equals("return");
    }

    private static boolean skipJsName(String name) {
        return name == null || name.equals("if") || name.equals("from") || name.equals("import");
    }

    private record Hit(int start, String symbol, String kind) {
    }
}
