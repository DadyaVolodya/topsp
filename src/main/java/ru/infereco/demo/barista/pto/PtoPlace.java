package ru.infereco.demo.barista.pto;

import java.util.List;

public record PtoPlace(
        String id,
        String name,
        String address,
        List<String> directionLabels,
        List<String> otherCampuses,
        String cardUrl,
        String routeToPlaceUrl,
        String routeToRedSquareUrl,
        String openUrl
) {

    public static final double RED_SQUARE_LAT = 55.7539;
    public static final double RED_SQUARE_LON = 37.6208;

    public String promptBlock() {
        String dirs = directionLabels == null || directionLabels.isEmpty()
                ? "не указаны"
                : String.join(", ", directionLabels);
        String extra = otherCampuses == null || otherCampuses.isEmpty()
                ? ""
                : " Другие корпуса того же учреждения: " + String.join("; ", otherCampuses) + ".";
        return """
                Факт с API ПТО (GET /api/pto/institutions/suggest и POST /api/pto/institutions/search). Не выдумывай другое учреждение.
                Название: %s
                Адрес: %s
                Направления на карточке: %s. Это не «Рисунок и живопись», туда не веди.
                Карточка на dev.aisto.local: %s
                Маршрут только через кнопку на карточке: «Постройте маршрут» или «Маршрут». Та же ссылка, что в aisto-front: %s
                Если спросили до Красной площади: %s
                Напарник сам откроет карточку, затем ту же ссылку «Постройте маршрут», что на сайте.
                Назови школу, адрес и что маршрут уже открываю.
                """.formatted(name, address, dirs, cardUrl, routeToPlaceUrl, routeToRedSquareUrl)
                + extra;
    }
}
