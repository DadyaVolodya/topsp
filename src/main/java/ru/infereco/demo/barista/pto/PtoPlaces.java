package ru.infereco.demo.barista.pto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class PtoPlaces {

    private static final Map<String, String> DIRECTION_LABELS = Map.of(
            "DRAWING_PAINTING", "Рисунок и живопись",
            "CHOREOGRAPHY", "Хореография",
            "DECORATIVE_APPLIED_ART", "Декоративно-прикладное искусство",
            "MUSICAL_INSTRUMENTS", "Музыкальные инструменты",
            "ARCHITECTURE_AND_DESIGN", "Архитектура и дизайн",
            "THEATRE", "Театр",
            "VOCAL", "Вокал",
            "CIRCUS", "Цирк",
            "ARCHITECTURE", "Архитектура");

    private PtoPlaces() {
    }

    static Optional<PtoPlace> fromSearch(JsonNode root, String standUrl, PtoQuery query) {
        JsonNode content = root == null ? null : root.get("content");
        if (content == null || !content.isArray() || content.isEmpty()) {
            return Optional.empty();
        }
        List<JsonNode> rows = new ArrayList<>();
        content.forEach(rows::add);
        JsonNode chosen = pick(rows);
        if (chosen == null) {
            return Optional.empty();
        }
        String id = text(chosen, "id");
        if (id.isBlank()) {
            return Optional.empty();
        }
        String name = firstNonBlank(text(chosen, "nameShort"), text(chosen, "nameFull"), text(chosen, "name"));
        String address = text(chosen, "addressFull");
        Double lat = number(chosen, "lat");
        Double lon = number(chosen, "lon");
        List<String> dirs = new ArrayList<>();
        JsonNode dirNode = chosen.get("directions");
        if (dirNode != null && dirNode.isArray()) {
            dirNode.forEach(item -> {
                String code = item.isTextual() ? item.asText() : text(item, "name");
                if (!code.isBlank()) {
                    dirs.add(DIRECTION_LABELS.getOrDefault(code, code));
                }
            });
        }
        List<String> others = new ArrayList<>();
        for (JsonNode row : rows) {
            if (id.equals(text(row, "id"))) {
                continue;
            }
            String otherAddr = text(row, "addressFull");
            if (!otherAddr.isBlank() && !others.contains(otherAddr)) {
                others.add(otherAddr);
            }
        }
        String card = cardUrl(standUrl, id);
        String toPlace = routeTo(lat, lon);
        String toRed = routeBetween(lat, lon, PtoPlace.RED_SQUARE_LAT, PtoPlace.RED_SQUARE_LON);
        String open = query.wantsRedSquare() ? toRed : (query.wantsRoute() ? toPlace : card);
        if (open.isBlank()) {
            open = card;
        }
        return Optional.of(new PtoPlace(id, name, address, List.copyOf(dirs), List.copyOf(others), card, toPlace, toRed, open));
    }

    static Optional<UUID> firstSuggestId(JsonNode root) {
        if (root == null || !root.isArray() || root.isEmpty()) {
            return Optional.empty();
        }
        String id = text(root.get(0), "id");
        try {
            return id.isBlank() ? Optional.empty() : Optional.of(UUID.fromString(id));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    static String cardUrl(String standUrl, String id) {
        String base = standUrl == null ? "" : standUrl.replaceAll("/$", "");
        return base + "/institutions/" + id;
    }

    static String routeTo(Double lat, Double lon) {
        if (lat == null || lon == null) {
            return "";
        }
        return "https://yandex.ru/maps/?rtext=~" + lat + "," + lon;
    }

    static String routeBetween(Double fromLat, Double fromLon, double toLat, double toLon) {
        if (fromLat == null || fromLon == null) {
            return "";
        }
        return "https://yandex.ru/maps/?rtext=" + fromLat + "," + fromLon + "~" + toLat + "," + toLon;
    }

    static JsonNode pick(List<JsonNode> rows) {
        JsonNode parent = null;
        JsonNode znamenka = null;
        for (JsonNode row : rows) {
            JsonNode parentId = row.get("parentId");
            if (parentId == null || parentId.isNull() || parentId.asText().isBlank()) {
                parent = row;
            }
            String addr = text(row, "addressFull").toLowerCase(Locale.ROOT);
            if (addr.contains("знаменк") && znamenka == null) {
                znamenka = row;
            }
        }
        if (parent != null) {
            return parent;
        }
        if (znamenka != null) {
            return znamenka;
        }
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.get(field) == null || node.get(field).isNull()) {
            return "";
        }
        return node.get(field).asText("").trim();
    }

    private static Double number(JsonNode node, String field) {
        if (node == null || node.get(field) == null || !node.get(field).isNumber()) {
            return null;
        }
        return node.get(field).asDouble();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
