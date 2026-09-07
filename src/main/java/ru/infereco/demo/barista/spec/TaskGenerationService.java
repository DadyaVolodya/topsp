package ru.infereco.demo.barista.spec;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import ru.infereco.demo.barista.code.CodeImpactService;

@Component
public class TaskGenerationService {

    private final DevTaskWriter writer;

    public TaskGenerationService(DevTaskWriter writer) {
        this.writer = writer;
    }

    public Path write(String fileName, List<SpecChange> changes) {
        List<SpecChange> meaningful = (changes == null ? List.<SpecChange>of() : changes).stream()
                .filter(change -> !change.excluded())
                .filter(change -> !"unchanged".equals(change.type()))
                .filter(change -> !"cosmetic".equals(change.significance()))
                .toList();
        if (meaningful.isEmpty()) {
            return null;
        }
        SpecChange first = meaningful.getFirst();
        String title = first.heading() == null || first.heading().isBlank()
                ? "Обновить реализацию по СП"
                : "Изменить: " + first.heading();
        return writer.writeMarkdown(fileName, markdown(title, fileName, meaningful));
    }

    static String markdown(String title, String fileName, List<SpecChange> changes) {
        StringBuilder out = new StringBuilder();
        out.append("# ").append(title).append("\n\n");
        out.append("Исполнитель: разработчик\n");
        out.append("Источник: ").append(fileName == null ? "СП" : fileName).append("\n\n");
        for (SpecChange change : changes) {
            out.append("## Причина\n");
            out.append("Изменение СП: раздел ").append(change.sectionPath())
                    .append(" (").append(change.type()).append(", ").append(change.significance()).append(").\n\n");
            out.append("## Было\n");
            out.append(empty(change.oldBehavior(), change.oldText())).append("\n\n");
            out.append("## Стало\n");
            out.append(empty(change.newBehavior(), change.newText())).append("\n\n");
            out.append("## Бизнес-смысл\n");
            out.append(empty(change.businessImpact(), change.summary())).append("\n\n");
            List<SpecChange.AffectedCode> front = change.affected().stream()
                    .filter(item -> CodeImpactService.frontend(item.repo(), item.path()))
                    .toList();
            List<SpecChange.AffectedCode> back = change.affected().stream()
                    .filter(item -> !CodeImpactService.frontend(item.repo(), item.path()))
                    .toList();
            out.append("## Потенциально затронутый frontend\n");
            appendAffected(out, front);
            out.append("\n## Потенциально затронутый backend\n");
            appendAffected(out, back);
            out.append("\n## Что требуется реализовать\n");
            out.append(change.summary() == null ? "Сверить код с новой формулировкой." : change.summary()).append("\n\n");
            out.append("## Acceptance Criteria\n");
            out.append("1. Поведение совпадает с «Стало» для раздела ").append(change.sectionPath()).append(".\n");
            out.append("2. Старое поведение («Было») больше не является единственным допустимым сценарием.\n");
            out.append("3. Есть проверка на фронте и/или бэке для затронутых символов.\n\n");
            out.append("## Риски / вопросы\n");
            out.append("- Формулировка «потенциально затронут», не «точно менять».\n");
            if (change.navigation() != null) {
                out.append("- Навигация для проверки: ").append(change.navigation().route())
                        .append(" target=").append(change.navigation().target()).append(".\n");
            }
            out.append("\n---\n\n");
        }
        return out.toString();
    }

    private static void appendAffected(StringBuilder out, List<SpecChange.AffectedCode> items) {
        if (items.isEmpty()) {
            out.append("- не найдено уверенной связи\n");
            return;
        }
        out.append(items.stream().map(item -> "- `" + item.path() + "`\n  - `" + item.symbol()
                + "`\n  - Причина: " + item.reason()
                + "\n  - Уверенность: " + Math.round(item.confidence() * 100) + "%").collect(Collectors.joining("\n")));
        out.append('\n');
    }

    private static String empty(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback == null || fallback.isBlank() ? "нет текста" : fallback;
    }

    public static boolean highImpact(SpecChange change) {
        if (change == null || change.excluded()) {
            return false;
        }
        String value = change.significance() == null ? "" : change.significance().toLowerCase(Locale.ROOT);
        return "critical".equals(value) || "high".equals(value);
    }
}
