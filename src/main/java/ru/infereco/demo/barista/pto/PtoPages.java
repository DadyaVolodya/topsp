package ru.infereco.demo.barista.pto;

import java.util.Locale;
import java.util.Optional;

/**
 * Страницы как в aisto-front {@code APP_ROUTES}:
 * /, /login, /institutions, /institutions/{id}, /compare, /grant-program, /profile.
 */
public final class PtoPages {

    private PtoPages() {
    }

    public static Optional<PtoPage> resolve(String text, String screenBrief, String siteUrl) {
        String base = siteUrl == null ? "" : siteUrl.trim().replaceAll("/$", "");
        if (base.isBlank()) {
            return Optional.empty();
        }
        String hay = ((text == null ? "" : text) + " " + (screenBrief == null ? "" : screenBrief))
                .toLowerCase(Locale.ROOT);
        if (!PtoQuery.alias(hay).isBlank()) {
            return Optional.empty();
        }
        if (!looksLikeSite(hay)) {
            return Optional.empty();
        }
        if (hay.contains("логин") || hay.contains("войти") || hay.contains("pto-pp") || hay.contains("/login")) {
            return Optional.of(page(base, "/login", "Вход"));
        }
        if (hay.contains("грант")) {
            return Optional.of(page(base, "/grant-program", "Грантовая программа"));
        }
        if (hay.contains("сравнен") || hay.contains("compare")) {
            return Optional.of(page(base, "/compare", "Сравнение"));
        }
        if (hay.contains("профил")) {
            return Optional.of(page(base, "/profile", "Профиль"));
        }
        String direction = direction(hay);
        if (direction != null) {
            return Optional.of(page(base, "/institutions?direction=" + direction, "Карта учебных заведений"));
        }
        if (hay.contains("главн") || hay.contains("домашн")) {
            return Optional.of(page(base, "/", "Главная"));
        }
        return Optional.of(page(base, "/institutions", "Карта учебных заведений"));
    }

    public static String siteUrl(String configured) {
        if (configured == null || configured.isBlank()) {
            return "https://dev.aisto.local";
        }
        return configured.trim().replaceAll("/$", "");
    }

    public static String apiUrl(String siteUrl, String configuredApi) {
        if (configuredApi != null && !configuredApi.isBlank()) {
            return configuredApi.trim().replaceAll("/$", "");
        }
        return siteUrl(siteUrl) + "/api";
    }

    static boolean looksLikeSite(String hay) {
        return hay.contains("aisto")
                || hay.contains("пто")
                || hay.contains("tvorchestvo")
                || hay.contains("творчеств")
                || hay.contains("учрежд")
                || hay.contains("заведен")
                || hay.contains("организац")
                || hay.contains("карта")
                || hay.contains("карте")
                || hay.contains("карту")
                || hay.contains("логин")
                || hay.contains("грант")
                || hay.contains("сравнен")
                || hay.contains("institutions")
                || hay.contains("гнесин")
                || hay.contains("гнесен")
                || hay.contains("несенн");
    }

    static String direction(String hay) {
        if (hay.contains("живопис") || hay.contains("рисун")) {
            return "DRAWING_PAINTING";
        }
        if (hay.contains("хореограф")) {
            return "CHOREOGRAPHY";
        }
        if (hay.contains("декоратив") || hay.contains("прикладн")) {
            return "DECORATIVE_APPLIED_ART";
        }
        if (hay.contains("музык") && !hay.contains("гнесин") && !hay.contains("гнесен") && !hay.contains("несенн")) {
            return "MUSICAL_INSTRUMENTS";
        }
        if (hay.contains("архитектур") || hay.contains("дизайн")) {
            return "ARCHITECTURE_AND_DESIGN";
        }
        if (hay.contains("театр")) {
            return "THEATRE";
        }
        if (hay.contains("вокал")) {
            return "VOCAL";
        }
        return null;
    }

    private static PtoPage page(String base, String path, String title) {
        return new PtoPage(title, path, base + path);
    }

    public record PtoPage(String title, String path, String openUrl) {
        public String promptBlock() {
            return """
                    Страница с фронта aisto-front (APP_ROUTES), открыть на dev.aisto.local.
                    Название: %s
                    Путь: %s
                    URL: %s
                    Напарник сам откроет эту страницу. Скажи куда кликнуть дальше по кадру, не перечисляй всё меню.
                    """.formatted(title, path, openUrl);
        }
    }
}
