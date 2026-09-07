package ru.infereco.demo.barista.skill;

import java.util.List;

public record Skill(String id, String title, List<String> triggers, String body) {

    public Skill {
        id = id == null ? "" : id.trim();
        title = title == null || title.isBlank() ? id : title.trim();
        triggers = triggers == null ? List.of() : List.copyOf(triggers);
        body = body == null ? "" : body.trim();
    }
}
