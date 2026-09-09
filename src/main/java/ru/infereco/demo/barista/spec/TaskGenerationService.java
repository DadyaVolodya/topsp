package ru.infereco.demo.barista.spec;

import java.nio.file.Path;
import java.util.ArrayList;
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
        List<SpecChange> meaningful = meaningful(changes);
        if (meaningful.isEmpty()) {
            return null;
        }
        SpecChange first = meaningful.getFirst();
        String title = first.heading() == null || first.heading().isBlank()
                ? "Обновить реализацию по СП"
                : "Изменить: " + first.heading();
        return writer.writeMarkdown(fileName, markdown(title, fileName, meaningful, null));
    }

    public List<Path> writeFrontAndBack(String fileName, List<SpecChange> changes) {
        List<SpecChange> meaningful = meaningful(changes);
        if (meaningful.isEmpty()) {
            return List.of();
        }
        List<Path> out = new ArrayList<>();
        String base = fileName == null ? "sp" : fileName;
        Path front = writer.writeMarkdown(
                "front-" + base,
                markdown("Frontend: " + titleOf(meaningful), base, meaningful, "front"));
        Path back = writer.writeMarkdown(
                "back-" + base,
                markdown("Backend: " + titleOf(meaningful), base, meaningful, "back"));
        if (front != null) {
            out.add(front);
        }
        if (back != null) {
            out.add(back);
        }
        return out;
    }

    /** Plain-text cards for Telegram: тема / описание / - / _ */
    public List<String> telegramCards(String fileName, List<SpecChange> changes) {
        List<SpecChange> meaningful = meaningful(changes);
        if (meaningful.isEmpty()) {
            return List.of();
        }
        String theme = titleOf(meaningful);
        return List.of(
                telegramCard("Frontend: " + theme, meaningful, "front"),
                telegramCard("Backend: " + theme, meaningful, "back"));
    }

    public String telegramCard(String theme, List<SpecChange> changes, String side) {
        StringBuilder out = new StringBuilder();
        out.append("тема: ").append(theme == null || theme.isBlank() ? "задача по СП" : theme).append('\n');
        out.append("описание:\n");
        for (SpecChange change : changes) {
            String summary = change.summary() == null || change.summary().isBlank()
                    ? change.sectionPath()
                    : change.summary();
            out.append("- ").append(clip(summary, 180)).append('\n');
            out.append("- было: ").append(clip(empty(change.oldBehavior(), change.oldText()), 160)).append('\n');
            out.append("- стало: ").append(clip(empty(change.newBehavior(), change.newText()), 160)).append('\n');
            List<SpecChange.AffectedCode> items = change.affected() == null ? List.of() : change.affected().stream()
                    .filter(item -> {
                        boolean front = CodeImpactService.frontend(item.repo(), item.path());
                        if ("front".equals(side)) {
                            return front;
                        }
                        if ("back".equals(side)) {
                            return !front;
                        }
                        return true;
                    })
                    .toList();
            if (items.isEmpty()) {
                out.append("- код: уверенной связи не найдено\n");
            } else {
                for (SpecChange.AffectedCode item : items) {
                    out.append("- ")
                            .append(item.path() == null ? "?" : item.path())
                            .append(" :: ")
                            .append(item.symbol() == null ? "?" : item.symbol())
                            .append('\n');
                }
            }
        }
        out.append('_');
        return out.toString();
    }

    private static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        String clean = text.replace('\n', ' ').replaceAll("\\s+", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, max) + "…";
    }

    private static List<SpecChange> meaningful(List<SpecChange> changes) {
        return (changes == null ? List.<SpecChange>of() : changes).stream()
                .filter(change -> !change.excluded())
                .filter(change -> !"unchanged".equals(change.type()))
                .filter(TaskGenerationService::actionable)
                .toList();
    }

    /** critical / high / medium - low и cosmetic не создают задачи и не уходят в Telegram. */
    public static boolean actionable(SpecChange change) {
        if (change == null || change.excluded()) {
            return false;
        }
        if ("unchanged".equals(change.type())) {
            return false;
        }
        String value = change.significance() == null ? "" : change.significance().toLowerCase(Locale.ROOT);
        return "critical".equals(value) || "high".equals(value) || "medium".equals(value);
    }

    private static String titleOf(List<SpecChange> changes) {
        SpecChange first = changes.getFirst();
        return first.heading() == null || first.heading().isBlank()
                ? "обновить реализацию по СП"
                : first.heading();
    }

    static String markdown(String title, String fileName, List<SpecChange> changes) {
        return markdown(title, fileName, changes, null);
    }

    static String markdown(String title, String fileName, List<SpecChange> changes, String side) {
        StringBuilder out = new StringBuilder();
        out.append("# ").append(title).append("\n\n");
        out.append("Исполнитель: ").append("front".equals(side) ? "frontend" : "back".equals(side) ? "backend" : "разработчик").append('\n');
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
            if (side == null || "front".equals(side)) {
                out.append("## Что менять на frontend\n");
                appendAffected(out, front);
                out.append('\n');
            }
            if (side == null || "back".equals(side)) {
                out.append("## Что менять на backend\n");
                appendAffected(out, back);
                out.append('\n');
            }
            out.append("## Что требуется реализовать\n");
            if ("front".equals(side)) {
                out.append("Обновить UI/флаги и вызовы API под «Стало». Символы: VisitRow, PetVisitsPage, visitsApi.\n\n");
            } else if ("back".equals(side)) {
                out.append("Обновить правила VisitService.canCancel / canEditDescription и ответы VisitController.\n\n");
            } else {
                out.append(change.summary() == null ? "Сверить код с новой формулировкой." : change.summary()).append("\n\n");
            }
            out.append("## Acceptance Criteria\n");
            out.append("1. Поведение совпадает с «Стало» для раздела ").append(change.sectionPath()).append(".\n");
            out.append("2. Старое поведение («Было») больше не является единственным допустимым сценарием.\n");
            out.append("3. Есть проверка для затронутых символов на этой стороне.\n\n");
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
