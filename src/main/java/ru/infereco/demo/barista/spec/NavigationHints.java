package ru.infereco.demo.barista.spec;

import java.util.Locale;
import ru.infereco.demo.barista.pto.PtoPages;

public final class NavigationHints {

    private NavigationHints() {
    }

    public static SpecChange.NavigationHint from(String heading, String text, String siteUrl) {
        String hay = ((heading == null ? "" : heading) + " " + (text == null ? "" : text))
                .toLowerCase(Locale.ROOT);
        String base = PtoPages.siteUrl(siteUrl);
        if (hay.contains("логин") || hay.contains("вход") || hay.contains("аутентиф")) {
            return hint("/login", "login-submit", base + "/login");
        }
        if (hay.contains("редактир") && (hay.contains("заявк") || hay.contains("заявлен"))) {
            return hint("/grant-program", "application-edit-button", base + "/grant-program");
        }
        if (hay.contains("заявк") || hay.contains("заявлен") || hay.contains("подач")) {
            return hint("/grant-program", "application-create-button", base + "/grant-program");
        }
        if (hay.contains("грант")) {
            return hint("/grant-program", "grant-program-root", base + "/grant-program");
        }
        if (hay.contains("сравнен")) {
            return hint("/compare", "compare-table", base + "/compare");
        }
        if (hay.contains("профил")) {
            return hint("/profile", "profile-form", base + "/profile");
        }
        if (hay.contains("маршрут") || hay.contains("доеха")) {
            return hint("/institutions", "build-route-button", base + "/institutions");
        }
        if (hay.contains("карт") || hay.contains("учрежд") || hay.contains("заведен")) {
            return hint("/institutions", "institutions-map", base + "/institutions");
        }
        return hint("/institutions", "app-root", base + "/institutions");
    }

    private static SpecChange.NavigationHint hint(String route, String target, String openUrl) {
        return new SpecChange.NavigationHint(route, target, "highlight", openUrl);
    }
}
