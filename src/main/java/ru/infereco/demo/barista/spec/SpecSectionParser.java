package ru.infereco.demo.barista.spec;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Делит СП на разделы: 1. / 1.1 / markdown-заголовки, иначе абзацы. */
public final class SpecSectionParser {

    private static final Pattern NUMBERED = Pattern.compile(
            "^\\s*(\\d+(?:\\.\\d+){0,5})[.)]?\\s+(\\S.*)$");
    private static final Pattern MARKDOWN = Pattern.compile("^#{1,4}\\s+(\\S.*)$");

    private SpecSectionParser() {
    }

    public static List<SpecSection> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String[] lines = raw.replace('\r', '\n').split("\n");
        List<SpecSection> sections = new ArrayList<>();
        String path = "";
        String heading = "Введение";
        StringBuilder body = new StringBuilder();
        boolean sawHeading = false;
        for (String line : lines) {
            String trimmed = line.trim();
            Matcher numbered = NUMBERED.matcher(trimmed);
            Matcher markdown = MARKDOWN.matcher(trimmed);
            if (numbered.matches() || markdown.matches()) {
                if (sawHeading || body.length() > 0) {
                    add(sections, path, heading, body.toString());
                    body.setLength(0);
                }
                if (numbered.matches()) {
                    path = numbered.group(1);
                    heading = numbered.group(2).trim();
                } else {
                    heading = markdown.group(1).trim();
                    path = slug(heading);
                }
                sawHeading = true;
            } else if (!trimmed.isEmpty()) {
                if (body.length() > 0) {
                    body.append('\n');
                }
                body.append(trimmed);
            }
        }
        add(sections, path.isBlank() ? "1" : path, heading, body.toString());
        if (sections.size() == 1 && sections.getFirst().heading().equals("Введение") && raw.length() > 1200) {
            return fallbackParagraphs(raw);
        }
        return mergeByPath(sections);
    }

    /** Оглавление Word дублирует номера разделов: оставляем самый полный текст. */
    static List<SpecSection> mergeByPath(List<SpecSection> sections) {
        if (sections == null || sections.isEmpty()) {
            return List.of();
        }
        Map<String, SpecSection> best = new LinkedHashMap<>();
        for (SpecSection section : sections) {
            String key = section.sectionPath() == null || section.sectionPath().isBlank()
                    ? slug(section.heading())
                    : section.sectionPath();
            SpecSection prev = best.get(key);
            if (prev == null || bodyLen(section) > bodyLen(prev)) {
                best.put(key, section);
            }
        }
        return List.copyOf(best.values());
    }

    private static int bodyLen(SpecSection section) {
        return (section.text() == null ? 0 : section.text().length())
                + (section.heading() == null ? 0 : section.heading().length());
    }

    private static List<SpecSection> fallbackParagraphs(String raw) {
        List<SpecSection> sections = new ArrayList<>();
        String[] parts = raw.split("\\R{2,}");
        int n = 0;
        StringBuilder current = new StringBuilder();
        for (String part : parts) {
            String piece = part.trim();
            if (piece.isEmpty()) {
                continue;
            }
            if (current.length() + piece.length() > 1400 && current.length() > 0) {
                n += 1;
                add(sections, "п." + n, "Фрагмент " + n, current.toString());
                current.setLength(0);
            }
            if (current.length() > 0) {
                current.append('\n');
            }
            current.append(piece);
        }
        if (current.length() > 0) {
            n += 1;
            add(sections, "п." + n, "Фрагмент " + n, current.toString());
        }
        return sections;
    }

    private static void add(List<SpecSection> sections, String path, String heading, String text) {
        String clean = text == null ? "" : text.trim();
        if (clean.isEmpty() && (heading == null || heading.isBlank())) {
            return;
        }
        String title = heading == null || heading.isBlank() ? path : heading;
        sections.add(new SpecSection(path, title, clean, hash(path + "\n" + title + "\n" + normalize(clean))));
    }

    public static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    public static String hash(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalize(text).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            return Integer.toHexString(normalize(text).hashCode());
        }
    }

    public static String slug(String heading) {
        if (heading == null || heading.isBlank()) {
            return "section";
        }
        String slug = heading.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "-");
        if (slug.length() > 40) {
            slug = slug.substring(0, 40);
        }
        return slug.isBlank() ? "section" : slug;
    }

    public static String documentId(String filename) {
        if (filename == null || filename.isBlank()) {
            return "spec";
        }
        String slug = filename.replaceAll("[^\\p{L}\\p{N}]+", "-").toLowerCase(Locale.ROOT);
        if (slug.length() > 72) {
            slug = slug.substring(0, 72);
        }
        return slug.isBlank() ? "spec" : slug;
    }
}
