package ru.infereco.demo.barista.skill;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class SkillRouter {

    private static final Pattern HASH = Pattern.compile("^\\s*#([\\w\\-а-яё]+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final SkillCatalog catalog;

    public SkillRouter(SkillCatalog catalog) {
        this.catalog = catalog;
    }

    public String route(boolean doom, String text, String screenBrief) {
        return route(doom, text, screenBrief, "topsp");
    }

    public String route(boolean doom, String text, String screenBrief, String current) {
        if (doom) {
            return "doom";
        }
        String hash = hashCommand(text);
        if (hash != null) {
            return switch (hash) {
                case "doom" -> "doom";
                case "hint" -> "hint";
                case "task", "sp", "sp1", "sp2", "topsp" -> "topsp";
                case "interview" -> "interview";
                default -> "topsp";
            };
        }
        String hay = ((text == null ? "" : text) + " " + (screenBrief == null ? "" : screenBrief))
                .toLowerCase(Locale.ROOT);
        String fallback = current == null || current.isBlank() || "doom".equals(current)
                ? "topsp"
                : current;
        String best = fallback;
        int bestScore = 0;
        int currentScore = 0;
        for (Skill skill : catalog.all()) {
            if ("doom".equals(skill.id()) || skill.triggers().isEmpty()) {
                continue;
            }
            int score = score(hay, skill);
            if (skill.id().equals(fallback)) {
                currentScore = score;
            }
            if (score > bestScore) {
                bestScore = score;
                best = skill.id();
            }
        }
        if (bestScore == 0) {
            return fallback;
        }
        if (currentScore == bestScore) {
            return fallback;
        }
        return best;
    }

    /** First #command token, lower-case, or null. */
    public static String hashCommand(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = HASH.matcher(text.trim());
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1).toLowerCase(Locale.ROOT);
    }

    static int score(String hay, Skill skill) {
        if (hay == null || hay.isBlank() || skill == null) {
            return 0;
        }
        int total = 0;
        for (String trigger : skill.triggers()) {
            if (trigger != null && trigger.length() >= 3 && hay.contains(trigger)) {
                total += trigger.length();
            }
        }
        return total;
    }
}
