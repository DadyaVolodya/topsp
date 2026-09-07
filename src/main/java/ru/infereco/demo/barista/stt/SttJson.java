package ru.infereco.demo.barista.stt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SttJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern TEXT = Pattern.compile("\"text\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern PARTIAL = Pattern.compile("\"partial\"\\s*:\\s*\"([^\"]*)\"");

    private SttJson() {
    }

    public static String partial(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        Matcher matcher = PARTIAL.matcher(json);
        return matcher.find() ? unescape(matcher.group(1)) : "";
    }

    public static String text(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        List<Hypothesis> alts = alternatives(json);
        if (!alts.isEmpty()) {
            return alts.getFirst().text();
        }
        Matcher matcher = TEXT.matcher(json);
        return matcher.find() ? unescape(matcher.group(1)) : "";
    }

    public static List<Hypothesis> alternatives(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode alts = root.path("alternatives");
            if (alts.isArray() && alts.size() > 0) {
                List<Hypothesis> list = new ArrayList<>();
                for (JsonNode node : alts) {
                    String text = node.path("text").asText("").trim().toLowerCase(Locale.ROOT);
                    if (!text.isBlank()) {
                        list.add(new Hypothesis(text, node.path("confidence").asDouble(0)));
                    }
                }
                list.sort(Comparator.comparingDouble(Hypothesis::confidence).reversed());
                return list;
            }
            String text = root.path("text").asText("").trim().toLowerCase(Locale.ROOT);
            if (!text.isBlank()) {
                return List.of(new Hypothesis(text, 1));
            }
        } catch (Exception ignored) {
            Matcher matcher = TEXT.matcher(json);
            if (matcher.find()) {
                return List.of(new Hypothesis(unescape(matcher.group(1)), 1));
            }
        }
        return List.of();
    }

    private static String unescape(String raw) {
        return raw.replace("\\n", " ").replace("\\\"", "\"").trim().toLowerCase(Locale.ROOT);
    }

    public record Hypothesis(String text, double confidence) {
    }
}
