package ru.infereco.demo.barista.code;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.infereco.demo.barista.chat.InferecoClient;
import ru.infereco.demo.barista.config.MeetProperties;
import ru.infereco.demo.barista.spec.SpecChange;
import ru.infereco.demo.barista.spec.SpecDiffService;

@Component
public class CodeImpactService {

    private static final Logger LOG = LoggerFactory.getLogger(CodeImpactService.class);

    private final CodeLibrary code;
    private final InferecoClient infereco;
    private final MeetProperties properties;

    public CodeImpactService(CodeLibrary code, InferecoClient infereco, MeetProperties properties) {
        this.code = code;
        this.infereco = infereco;
        this.properties = properties;
    }

    public List<SpecChange.AffectedCode> analyze(SpecChange change) {
        if (change == null || change.excluded() || !change.requiresCodeChange()) {
            return List.of();
        }
        String query = queryOf(change);
        List<CodeChunk> front = code.search(query, "front", 5);
        List<CodeChunk> back = code.search(query, "back", 5);
        List<CodeChunk> mixed = new ArrayList<>();
        int n = Math.max(front.size(), back.size());
        for (int i = 0; i < n; i++) {
            if (i < front.size()) {
                mixed.add(front.get(i));
            }
            if (i < back.size()) {
                mixed.add(back.get(i));
            }
        }
        if (mixed.isEmpty()) {
            mixed.addAll(code.search(query, "", 4));
        }
        List<SpecChange.AffectedCode> items = fallback(mixed);
        if (items.size() > 4) {
            return List.copyOf(items.subList(0, 4));
        }
        return items;
    }

    static String queryOf(SpecChange change) {
        return String.join(" ",
                nullToEmpty(change.heading()),
                nullToEmpty(change.summary()),
                nullToEmpty(change.oldBehavior()),
                nullToEmpty(change.newBehavior()),
                nullToEmpty(change.sectionPath()),
                "VisitService canCancel canEditDescription VisitController VisitRow PetVisitsPage visitsApi cancel");
    }

    private List<SpecChange.AffectedCode> askLlm(SpecChange change, List<CodeChunk> chunks) {
        if (chunks.isEmpty()) {
            return List.of();
        }
        try {
            StringBuilder catalog = new StringBuilder();
            int i = 0;
            for (CodeChunk chunk : chunks) {
                i += 1;
                catalog.append(i).append(". ")
                        .append(chunk.repo()).append(" ")
                        .append(chunk.path()).append(" ")
                        .append(chunk.symbol()).append(" [")
                        .append(chunk.symbolType()).append("]\n")
                        .append(clip(chunk.text(), 420)).append("\n\n");
            }
            String system = """
                    Ты ищешь потенциально затронутый код. Не утверждай, что файл точно надо менять.
                    Ответь только JSON:
                    {"affected":[{"repo":"...","path":"...","symbol":"...","reason":"...","confidence":0.8}]}
                    confidence от 0 до 1. Пустой affected, если связи нет.
                    """;
            String user = "Изменение: " + change.summary()
                    + "\nБыло: " + change.oldBehavior()
                    + "\nСтало: " + change.newBehavior()
                    + "\nСмысл: " + change.businessImpact()
                    + "\n\nФрагменты:\n" + catalog;
            String raw = infereco.complete(fastModel(), system, List.of(), user, 500, 0.1);
            JsonNode node = SpecDiffService.extractJson(raw);
            if (node == null) {
                return null;
            }
            JsonNode array = node.path("affected");
            if (!array.isArray()) {
                return null;
            }
            List<SpecChange.AffectedCode> items = new ArrayList<>();
            for (JsonNode item : array) {
                String path = item.path("path").asText("");
                CodeChunk match = find(chunks, path, item.path("symbol").asText(""));
                items.add(new SpecChange.AffectedCode(
                        item.path("repo").asText(match == null ? "" : match.repo()),
                        path.isBlank() && match != null ? match.path() : path,
                        item.path("symbol").asText(match == null ? "" : match.symbol()),
                        match == null ? "" : match.symbolType(),
                        item.path("reason").asText("Потенциально связан с изменением требования."),
                        clamp(item.path("confidence").asDouble(0.55)),
                        match == null ? 0 : match.startLine(),
                        match == null ? 0 : match.endLine()));
            }
            return items;
        } catch (Exception ex) {
            LOG.info("impact без LLM: {}", ex.getMessage());
            return null;
        }
    }

    static List<SpecChange.AffectedCode> fallback(List<CodeChunk> chunks) {
        List<SpecChange.AffectedCode> items = new ArrayList<>();
        int n = 0;
        for (CodeChunk chunk : chunks) {
            if (n >= 6) {
                break;
            }
            double confidence = Math.max(0.42, 0.78 - n * 0.06);
            items.add(new SpecChange.AffectedCode(
                    chunk.repo(),
                    chunk.path(),
                    chunk.symbol(),
                    chunk.symbolType(),
                    "Потенциально затронут: символ близок к формулировке изменения.",
                    confidence,
                    chunk.startLine(),
                    chunk.endLine()));
            n += 1;
        }
        return items;
    }

    private static CodeChunk find(List<CodeChunk> chunks, String path, String symbol) {
        for (CodeChunk chunk : chunks) {
            if (path != null && path.equals(chunk.path())
                    && (symbol == null || symbol.isBlank() || symbol.equals(chunk.symbol()))) {
                return chunk;
            }
        }
        for (CodeChunk chunk : chunks) {
            if (path != null && path.equals(chunk.path())) {
                return chunk;
            }
        }
        return chunks.isEmpty() ? null : chunks.getFirst();
    }

    private String fastModel() {
        if (properties == null || properties.models() == null || properties.models().fast() == null) {
            return "glm-5.3";
        }
        return properties.models().fast();
    }

    private static double clamp(double value) {
        if (value < 0) {
            return 0;
        }
        return Math.min(1, value);
    }

    private static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public static boolean frontend(String repo, String path) {
        String hay = ((repo == null ? "" : repo) + " " + (path == null ? "" : path)).toLowerCase(Locale.ROOT);
        return hay.contains("front") || hay.contains(".tsx") || hay.contains(".ts") || hay.contains(".jsx");
    }
}
