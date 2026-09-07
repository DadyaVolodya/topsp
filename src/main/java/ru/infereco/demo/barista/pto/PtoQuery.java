package ru.infereco.demo.barista.pto;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Разбор реплики: что искать в API ПТО и какую ссылку открыть. */
public record PtoQuery(String term, boolean wantsRoute, boolean wantsRedSquare, boolean looksLikePlace) {

    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern STOP = Pattern.compile(
            "(?iU)\\b(как|пожалуйста|скажи|подскажи|покажи|открой|найди|найти|доехать|дойти"
                    + "|добраться|добрать|маршрут|проезд|дорога|попасть|ехать|идти"
                    + "|школы|школу|школа|колледж|учреждения|учреждение|заведения|заведение"
                    + "|учебн\\w*|сайт|карте|карта|пто|логин|войти|организац\\w*|aisto)\\b");
    private static final Set<String> GENERIC = Set.of(
            "пто", "сайт", "карта", "карт", "творчество", "направление", "направления", "логин");

    public static PtoQuery parse(String text, String screenBrief) {
        PtoQuery fromUser = parse(text);
        if (fromUser.searchable()) {
            return fromUser;
        }
        String extra = screenBrief == null ? "" : screenBrief.trim();
        if (extra.isBlank()) {
            return fromUser;
        }
        String joined = (text == null ? "" : text.trim()) + " " + extra;
        PtoQuery fromScreen = parse(joined);
        return fromScreen.searchable() ? fromScreen : fromUser;
    }

    public static PtoQuery parse(String text) {
        String raw = text == null ? "" : text.trim();
        String lower = raw.toLowerCase(Locale.ROOT);
        boolean red = lower.contains("красн");
        boolean route = red
                || lower.contains("добрать")
                || lower.contains("доеха")
                || lower.contains("дойти")
                || lower.contains("маршрут")
                || lower.contains("проезд")
                || lower.contains("как попасть")
                || lower.contains("как ехать");
        boolean place = !alias(lower).isBlank()
                || lower.contains("школ")
                || lower.contains("колледж")
                || lower.contains("учрежд")
                || lower.contains("заведен")
                || lower.contains("tvorchestvo")
                || lower.contains("aisto")
                || lower.contains("пто")
                || lower.contains("маршрут")
                || lower.contains("карте")
                || lower.contains("карта");
        String term = alias(lower);
        if (term.isBlank()) {
            String stripped = SPACES.matcher(STOP.matcher(lower).replaceAll(" ")).replaceAll(" ").trim();
            term = stripped;
        }
        return new PtoQuery(term, route, red, place);
    }

    public boolean searchable() {
        if (!looksLikePlace || term == null) {
            return false;
        }
        String value = term.trim();
        if (value.length() < 4 || value.length() > 48) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (GENERIC.contains(lower)) {
            return false;
        }
        if (lower.contains("aisto") || lower.contains("организац") || lower.contains("страниц") || lower.contains("карту")) {
            return !alias(lower).isBlank();
        }
        return true;
    }

    static String alias(String lower) {
        if (lower.contains("гнесен")
                || lower.contains("гнесин")
                || lower.contains("gnesin")
                || lower.contains("несенных")
                || lower.contains("несенной")) {
            return "Гнесин";
        }
        return "";
    }
}
