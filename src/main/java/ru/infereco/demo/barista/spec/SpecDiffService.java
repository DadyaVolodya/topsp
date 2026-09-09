package ru.infereco.demo.barista.spec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.infereco.demo.barista.chat.InferecoClient;
import ru.infereco.demo.barista.config.MeetProperties;

@Component
public class SpecDiffService {

    private static final Logger LOG = LoggerFactory.getLogger(SpecDiffService.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int LLM_LIMIT = 8;

    private final InferecoClient infereco;
    private final MeetProperties properties;

    public SpecDiffService(InferecoClient infereco, MeetProperties properties) {
        this.infereco = infereco;
        this.properties = properties;
    }

    public List<SpecChange> diff(SpecVersionSnap oldSnap, SpecVersionSnap next, String siteUrl) {
        if (next == null) {
            return List.of();
        }
        List<Pair> pairs = match(
                oldSnap == null ? List.of() : oldSnap.sections(),
                next.sections());
        List<SpecChange> changes = new ArrayList<>();
        for (Pair pair : pairs) {
            SpecSection oldSection = pair.oldSection();
            SpecSection newSection = pair.newSection();
            if (oldSection != null && newSection != null && oldSection.hash().equals(newSection.hash())) {
                continue;
            }
            if (noise(oldSection, newSection, pair.type())) {
                continue;
            }
            String type = pair.type();
            Draft draft = fallback(oldSection, newSection, type);
            if ("unchanged".equals(draft.type) || "cosmetic".equals(draft.significance)) {
                continue;
            }
            String heading = newSection != null ? newSection.heading() : oldSection.heading();
            String path = newSection != null ? newSection.sectionPath() : oldSection.sectionPath();
            String oldText = oldSection == null ? "" : oldSection.text();
            String newText = newSection == null ? "" : newSection.text();
            changes.add(SpecChange.create(
                    UUID.randomUUID().toString(),
                    next.documentId(),
                    oldSnap == null ? 0 : oldSnap.version(),
                    next.version(),
                    draft.type,
                    draft.significance,
                    path,
                    heading,
                    draft.summary,
                    draft.oldBehavior,
                    draft.newBehavior,
                    draft.businessImpact,
                    draft.requiresCodeChange,
                    oldText,
                    newText,
                    NavigationHints.from(heading, oldText + "\n" + newText, siteUrl)));
        }
        refineWithLlm(changes, pairs);
        return changes;
    }

    private void refineWithLlm(List<SpecChange> changes, List<Pair> pairs) {
        if (changes.size() > 12) {
            return;
        }
        int llmLeft = Math.min(LLM_LIMIT, changes.size());
        for (int i = 0; i < changes.size() && llmLeft > 0; i++) {
            SpecChange change = changes.get(i);
            if (!"high".equals(change.significance()) && !"critical".equals(change.significance())) {
                continue;
            }
            Pair pair = pairOf(pairs, change.sectionPath());
            if (pair == null) {
                continue;
            }
            boolean policy = policyShift(change.oldText(), change.newText());
            Draft llm = askLlm(pair.oldSection(), pair.newSection(), pair.neighbors(), change.type());
            if (llm == null) {
                continue;
            }
            llmLeft -= 1;
            if ("unchanged".equals(llm.type) || "cosmetic".equals(llm.significance)) {
                // эвристика «запрещено/можно» важнее мнения модели
                if (policy) {
                    continue;
                }
                changes.remove(i);
                i -= 1;
                continue;
            }
            String significance = llm.significance == null || llm.significance.isBlank()
                    ? change.significance()
                    : llm.significance.toLowerCase(Locale.ROOT);
            if (policy && rank(significance) < rank(change.significance())) {
                significance = change.significance();
            }
            String type = llm.type == null || llm.type.isBlank() ? change.type() : llm.type.toLowerCase(Locale.ROOT);
            if (policy && ("unchanged".equals(type) || "cosmetic".equals(type))) {
                type = change.type();
            }
            changes.set(i, SpecChange.create(
                    change.id(),
                    change.documentId(),
                    change.fromVersion(),
                    change.toVersion(),
                    type,
                    significance,
                    change.sectionPath(),
                    change.heading(),
                    llm.summary.isBlank() ? change.summary() : llm.summary,
                    llm.oldBehavior.isBlank() ? change.oldBehavior() : llm.oldBehavior,
                    llm.newBehavior.isBlank() ? change.newBehavior() : llm.newBehavior,
                    llm.businessImpact.isBlank() ? change.businessImpact() : llm.businessImpact,
                    policy || llm.requiresCodeChange,
                    change.oldText(),
                    change.newText(),
                    change.navigation()));
        }
    }

    static boolean policyShift(String oldText, String newText) {
        String grade = significanceOf(oldText, newText);
        return "high".equals(grade) || "critical".equals(grade);
    }

    private static int rank(String significance) {
        if (significance == null) {
            return 0;
        }
        return switch (significance.toLowerCase(Locale.ROOT)) {
            case "critical" -> 5;
            case "high" -> 4;
            case "medium" -> 3;
            case "low" -> 2;
            case "cosmetic" -> 1;
            default -> 0;
        };
    }

    private static Pair pairOf(List<Pair> pairs, String path) {
        for (Pair pair : pairs) {
            SpecSection section = pair.newSection() != null ? pair.newSection() : pair.oldSection();
            if (section != null && path.equals(section.sectionPath())) {
                return pair;
            }
        }
        return null;
    }

    static boolean noise(SpecSection oldSection, SpecSection newSection, String type) {
        String oldText = oldSection == null ? "" : oldSection.text();
        String newText = newSection == null ? "" : newSection.text();
        String heading = newSection != null ? newSection.heading() : (oldSection == null ? "" : oldSection.heading());
        if (stubHeading(heading) && oldText.length() < 40 && newText.length() < 40) {
            return true;
        }
        if (("added".equals(type) || "removed".equals(type))
                && Math.max(oldText.length(), newText.length()) < 24
                && stubHeading(heading)) {
            return true;
        }
        return false;
    }

    private static boolean stubHeading(String heading) {
        String n = SpecSectionParser.normalize(heading);
        return n.equals("описание")
                || n.equals("макеты")
                || n.startsWith("требования к")
                || n.startsWith("критерии")
                || n.equals("логирование")
                || n.startsWith("основные сведения")
                || n.startsWith("входные параметры")
                || n.startsWith("выходные параметры")
                || n.startsWith("основной сценарий");
    }

    static List<Pair> match(List<SpecSection> oldSections, List<SpecSection> newSections) {
        List<SpecSection> unusedOld = new ArrayList<>(oldSections == null ? List.of() : oldSections);
        List<SpecSection> unusedNew = new ArrayList<>(newSections == null ? List.of() : newSections);
        List<Pair> pairs = new ArrayList<>();
        takeExactPath(unusedOld, unusedNew, pairs);
        takeHeading(unusedOld, unusedNew, pairs);
        takeSimilar(unusedOld, unusedNew, pairs);
        for (SpecSection left : unusedOld) {
            pairs.add(new Pair(left, null, "removed", neighbors(oldSections, left)));
        }
        for (SpecSection added : unusedNew) {
            pairs.add(new Pair(null, added, "added", neighbors(newSections, added)));
        }
        return pairs;
    }

    private static void takeExactPath(List<SpecSection> olds, List<SpecSection> news, List<Pair> pairs) {
        for (int i = news.size() - 1; i >= 0; i--) {
            SpecSection next = news.get(i);
            int found = indexByPath(olds, next.sectionPath());
            if (found >= 0) {
                SpecSection prev = olds.remove(found);
                news.remove(i);
                String type = prev.hash().equals(next.hash()) ? "unchanged" : "modified";
                pairs.add(new Pair(prev, next, type, ""));
            }
        }
    }

    private static void takeHeading(List<SpecSection> olds, List<SpecSection> news, List<Pair> pairs) {
        for (int i = news.size() - 1; i >= 0; i--) {
            SpecSection next = news.get(i);
            int found = indexByHeading(olds, next.heading());
            if (found >= 0) {
                SpecSection prev = olds.remove(found);
                news.remove(i);
                String type = prev.sectionPath().equals(next.sectionPath())
                        ? (prev.hash().equals(next.hash()) ? "unchanged" : "modified")
                        : "moved";
                pairs.add(new Pair(prev, next, type, ""));
            }
        }
    }

    private static void takeSimilar(List<SpecSection> olds, List<SpecSection> news, List<Pair> pairs) {
        for (int i = news.size() - 1; i >= 0; i--) {
            SpecSection next = news.get(i);
            int best = -1;
            double score = 0.55;
            for (int j = 0; j < olds.size(); j++) {
                double sim = similarity(olds.get(j), next);
                if (sim > score) {
                    score = sim;
                    best = j;
                }
            }
            if (best >= 0) {
                SpecSection prev = olds.remove(best);
                news.remove(i);
                String type = prev.sectionPath().equals(next.sectionPath()) ? "modified" : "moved";
                pairs.add(new Pair(prev, next, type, ""));
            }
        }
    }

    static double similarity(SpecSection left, SpecSection right) {
        Set<String> a = words((left.heading() + " " + left.text()));
        Set<String> b = words((right.heading() + " " + right.text()));
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        int inter = 0;
        for (String word : a) {
            if (b.contains(word)) {
                inter += 1;
            }
        }
        return (double) inter / (a.size() + b.size() - inter);
    }

    static Draft fallback(SpecSection oldSection, SpecSection newSection, String type) {
        if ("added".equals(type)) {
            boolean stub = newSection.text() == null || newSection.text().length() < 40;
            return new Draft(
                    "added",
                    stub ? "low" : "high",
                    "Добавлено требование: " + clip(newSection.heading(), 80),
                    "",
                    clip(newSection.text(), 240),
                    stub ? "Заголовок без тела. Проверить, не оглавление ли это."
                            : "Появился новый сценарий, его нужно отразить в продукте.",
                    !stub);
        }
        if ("removed".equals(type)) {
            boolean stub = oldSection.text() == null || oldSection.text().length() < 40;
            return new Draft(
                    "removed",
                    stub ? "low" : "high",
                    "Удалено требование: " + clip(oldSection.heading(), 80),
                    clip(oldSection.text(), 240),
                    "",
                    stub ? "Исчез короткий заголовок."
                            : "Сценарий больше не описан в СП. Проверить, не осталась ли мёртвая логика.",
                    !stub);
        }
        String oldText = oldSection == null ? "" : oldSection.text();
        String newText = newSection == null ? "" : newSection.text();
        String significance = significanceOf(oldText, newText);
        boolean code = !"cosmetic".equals(significance) && !"low".equals(significance);
        return new Draft(
                type == null ? "modified" : type,
                significance,
                "Изменён раздел " + (newSection == null ? oldSection.sectionPath() : newSection.sectionPath())
                        + ": " + clip(newSection == null ? oldSection.heading() : newSection.heading(), 80),
                clip(oldText, 240),
                clip(newText, 240),
                "Нужно сверить поведение продукта с новой формулировкой.",
                code);
    }

    static String significanceOf(String oldText, String newText) {
        String oldN = SpecSectionParser.normalize(oldText);
        String newN = SpecSectionParser.normalize(newText);
        if (oldN.equals(newN)) {
            return "cosmetic";
        }
        if (forbidden(oldN) != forbidden(newN)) {
            return forbidden(oldN) && allowed(newN) || forbidden(newN) && allowed(oldN) ? "high" : "critical";
        }
        if (modalShift(oldN, newN)) {
            return "high";
        }
        int delta = Math.abs(oldN.length() - newN.length());
        if (delta < 24 && similarityWords(oldN, newN) > 0.86) {
            return "low";
        }
        return "medium";
    }

    private static boolean forbidden(String text) {
        return text.contains("не может")
                || text.contains("нельзя")
                || text.contains("запрещ")
                || text.contains("не доступ");
    }

    private static boolean allowed(String text) {
        return (text.contains("может") || text.contains("можно") || text.contains("доступ"))
                && !forbidden(text);
    }

    private static boolean modalShift(String oldN, String newN) {
        String[] keys = {"обязан", "должен", "вправе"};
        for (String key : keys) {
            if (oldN.contains(key) != newN.contains(key)) {
                return true;
            }
        }
        return allowed(oldN) != allowed(newN);
    }

    private static double similarityWords(String left, String right) {
        Set<String> a = words(left);
        Set<String> b = words(right);
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        int inter = 0;
        for (String word : a) {
            if (b.contains(word)) {
                inter += 1;
            }
        }
        return (double) inter / (a.size() + b.size() - inter);
    }

    private Draft askLlm(SpecSection oldSection, SpecSection newSection, String neighbors, String fallbackType) {
        try {
            String system = """
                    Сравни два фрагмента системной постановки. Ответь только JSON без markdown:
                    {"type":"added|removed|modified|unchanged|moved","significance":"critical|high|medium|low|cosmetic",
                    "summary":"...","oldBehavior":"...","newBehavior":"...","businessImpact":"...","requiresCodeChange":true}
                    Переформулировка без смены смысла = unchanged или cosmetic.
                    Замена «может» на «обязан» = high и requiresCodeChange true.
                    Смена «не может/нельзя/запрещено» на «может/можно» (или обратно) = high или critical, никогда low/cosmetic.
                    """;
            String user = "Тип-подсказка: " + fallbackType
                    + "\nСоседи:\n" + clip(neighbors, 400)
                    + "\n\nБЫЛО:\n" + clip(oldSection == null ? "" : oldSection.heading() + "\n" + oldSection.text(), 1200)
                    + "\n\nСТАЛО:\n" + clip(newSection == null ? "" : newSection.heading() + "\n" + newSection.text(), 1200);
            String raw = infereco.complete(fastModel(), system, List.of(), user, 420, 0.1);
            JsonNode node = extractJson(raw);
            if (node == null) {
                return null;
            }
            String type = node.path("type").asText(fallbackType);
            if (type.isBlank()) {
                type = fallbackType;
            }
            return new Draft(
                    type.toLowerCase(Locale.ROOT),
                    node.path("significance").asText("medium"),
                    node.path("summary").asText(""),
                    node.path("oldBehavior").asText(""),
                    node.path("newBehavior").asText(""),
                    node.path("businessImpact").asText(""),
                    node.path("requiresCodeChange").asBoolean(true));
        } catch (Exception ex) {
            LOG.info("semantic diff без LLM: {}", ex.getMessage());
            return null;
        }
    }

    private String fastModel() {
        if (properties == null || properties.models() == null || properties.models().fast() == null) {
            return "glm-5.3";
        }
        return properties.models().fast();
    }

    public static JsonNode extractJson(String raw) {
        if (raw == null) {
            return null;
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return JSON.readTree(raw.substring(start, end + 1));
        } catch (Exception ex) {
            return null;
        }
    }

    private static int indexByPath(List<SpecSection> list, String path) {
        for (int i = 0; i < list.size(); i++) {
            if (path != null && path.equals(list.get(i).sectionPath())) {
                return i;
            }
        }
        return -1;
    }

    private static int indexByHeading(List<SpecSection> list, String heading) {
        String want = SpecSectionParser.normalize(heading);
        for (int i = 0; i < list.size(); i++) {
            if (want.equals(SpecSectionParser.normalize(list.get(i).heading()))) {
                return i;
            }
        }
        return -1;
    }

    private static String neighbors(List<SpecSection> all, SpecSection current) {
        if (all == null || current == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (SpecSection section : all) {
            if (section.sectionPath().equals(current.sectionPath())) {
                continue;
            }
            if (section.sectionPath().startsWith(current.sectionPath())
                    || current.sectionPath().startsWith(section.sectionPath())) {
                out.append(section.sectionPath()).append(' ').append(section.heading()).append('\n');
            }
        }
        return out.toString();
    }

    private static Set<String> words(String text) {
        Set<String> words = new HashSet<>();
        if (text == null) {
            return words;
        }
        for (String part : SpecSectionParser.normalize(text).split(" ")) {
            if (part.length() > 2) {
                words.add(part);
            }
        }
        return words;
    }

    private static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        String clean = text.trim();
        return clean.length() <= max ? clean : clean.substring(0, max) + "…";
    }

    public static Map<String, Integer> summary(List<SpecChange> changes) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("total", 0);
        counts.put("critical", 0);
        counts.put("high", 0);
        counts.put("medium", 0);
        counts.put("low", 0);
        counts.put("cosmetic", 0);
        if (changes == null) {
            return counts;
        }
        for (SpecChange change : changes) {
            if (change.excluded()) {
                continue;
            }
            counts.put("total", counts.get("total") + 1);
            String key = change.significance() == null ? "medium" : change.significance().toLowerCase(Locale.ROOT);
            counts.put(key, counts.getOrDefault(key, 0) + 1);
        }
        return counts;
    }

    record Pair(SpecSection oldSection, SpecSection newSection, String type, String neighbors) {
    }

    record Draft(
            String type,
            String significance,
            String summary,
            String oldBehavior,
            String newBehavior,
            String businessImpact,
            boolean requiresCodeChange
    ) {
    }
}
