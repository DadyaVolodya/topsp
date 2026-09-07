package ru.infereco.demo.barista.skill;

import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class SkillRouter {

    private final SkillCatalog catalog;

    public SkillRouter(SkillCatalog catalog) {
        this.catalog = catalog;
    }

    public String route(boolean doom, String text, String screenBrief) {
        return route(doom, text, screenBrief, "interview");
    }

    public String route(boolean doom, String text, String screenBrief, String current) {
        if (doom) {
            return "doom";
        }
        String hay = ((text == null ? "" : text) + " " + (screenBrief == null ? "" : screenBrief))
                .toLowerCase(Locale.ROOT);
        String fallback = current == null || current.isBlank() || "doom".equals(current)
                ? "interview"
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
