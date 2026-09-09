package ru.infereco.demo.barista.chat;

import ru.infereco.demo.barista.spec.SpecChange;

public record ChatContext(String documentId, String changeId, String fileName, SpecChange change) {
    public String describe() {
        StringBuilder text = new StringBuilder();
        text.append("Документ: ").append(fileName).append("\n")
                .append("Изменение: ").append(changeId).append("\n")
                .append("Версии: ").append(change.fromVersion()).append(" > ").append(change.toVersion()).append("\n")
                .append("Раздел: ").append(change.sectionPath()).append(" ").append(change.heading()).append("\n")
                .append("Было:\n").append(change.oldText()).append("\nСтало:\n").append(change.newText())
                .append("\nБизнес-смысл: ").append(change.businessImpact()).append("\nПотенциально затронутый код:\n");
        if (change.affected() != null) {
            for (var item : change.affected()) {
                text.append(item.repo()).append(" / ").append(item.path()).append(" :: ").append(item.symbol())
                        .append(" — ").append(item.reason()).append("\n");
            }
        }
        return text.toString();
    }
}
