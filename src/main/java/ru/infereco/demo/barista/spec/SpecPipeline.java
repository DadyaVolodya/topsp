package ru.infereco.demo.barista.spec;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.infereco.demo.barista.code.CodeImpactService;
import ru.infereco.demo.barista.config.MeetProperties;
import ru.infereco.demo.barista.metrics.MetricsRegistry;

@Component
public class SpecPipeline {

    private static final Logger LOG = LoggerFactory.getLogger(SpecPipeline.class);

    private final SpecVersionStore store;
    private final SpecDiffService diff;
    private final CodeImpactService impact;
    private final TaskGenerationService tasks;
    private final TelegramNotifier telegram;
    private final MetricsRegistry metrics;
    private final MeetProperties properties;
    private volatile String lastNotice = "";

    public SpecPipeline(
            SpecVersionStore store,
            SpecDiffService diff,
            CodeImpactService impact,
            TaskGenerationService tasks,
            TelegramNotifier telegram,
            MetricsRegistry metrics,
            MeetProperties properties
    ) {
        this.store = store;
        this.diff = diff;
        this.impact = impact;
        this.tasks = tasks;
        this.telegram = telegram;
        this.metrics = metrics;
        this.properties = properties;
    }

    public synchronized SpecVersionSnap onSpecLoaded(String fileName, String text, boolean notify) {
        String documentId = SpecSectionParser.documentId(fileName);
        long print = fingerprint(text);
        SpecVersionSnap last = store.latest(documentId);
        if (last != null && last.fingerprint() == print) {
            if (needsRepair(last)) {
                return repairAndAnalyze(last, notify);
            }
            return last;
        }
        boolean replaceGarbage = last != null
                && SpecText.looksLikeRawMarkup(last.rawText())
                && !SpecText.looksLikeRawMarkup(text);
        int version = last == null ? 1 : (replaceGarbage ? last.version() : last.version() + 1);
        SpecVersionSnap snap = new SpecVersionSnap(
                documentId,
                fileName,
                version,
                print,
                Instant.now().toString(),
                text,
                SpecSectionParser.parse(text));
        store.save(snap);
        LOG.info("СП {} версия {}", fileName, version);
        if (version == 1) {
            return snap;
        }
        return analyze(documentId, notify || replaceGarbage);
    }

    private boolean needsRepair(SpecVersionSnap snap) {
        if (snap == null || snap.sections() == null || snap.sections().isEmpty()) {
            return true;
        }
        long unique = snap.sections().stream().map(SpecSection::sectionPath).distinct().count();
        return snap.sections().size() > unique + 8;
    }

    private SpecVersionSnap repairAndAnalyze(SpecVersionSnap last, boolean notify) {
        SpecVersionSnap previous = store.previous(last.documentId());
        if (previous != null) {
            store.save(reparsed(previous));
        }
        store.save(reparsed(last));
        LOG.info("СП {} переразобрал разделы, запускаю diff", last.fileName());
        return analyze(last.documentId(), notify);
    }

    private static SpecVersionSnap reparsed(SpecVersionSnap snap) {
        return new SpecVersionSnap(
                snap.documentId(),
                snap.fileName(),
                snap.version(),
                snap.fingerprint(),
                snap.createdAt(),
                snap.rawText(),
                SpecSectionParser.parse(snap.rawText()));
    }

    public synchronized SpecVersionSnap analyze(String documentId, boolean notify) {
        SpecVersionSnap current = store.latest(documentId);
        if (current == null) {
            return null;
        }
        SpecVersionSnap previous = store.previous(documentId);
        long t0 = System.currentTimeMillis();
        List<SpecChange> changes = diff.diff(previous, current, siteUrl());
        long diffMs = System.currentTimeMillis() - t0;
        long t1 = System.currentTimeMillis();
        List<RequirementCodeLink> links = new ArrayList<>();
        int impactLeft = 8;
        for (int i = 0; i < changes.size(); i++) {
            SpecChange change = changes.get(i);
            if (impactLeft <= 0 || change.excluded() || !TaskGenerationService.highImpact(change)) {
                continue;
            }
            SpecChange filled = change.withAffected(impact.analyze(change));
            changes.set(i, filled);
            impactLeft -= 1;
            for (SpecChange.AffectedCode item : filled.affected()) {
                links.add(new RequirementCodeLink(
                        filled.id(),
                        current.version(),
                        item.repo(),
                        item.path(),
                        item.symbol(),
                        item.reason(),
                        item.confidence()));
            }
        }
        long impactMs = System.currentTimeMillis() - t1;
        store.saveChanges(documentId, changes);
        store.saveLinks(documentId, links);
        long t2 = System.currentTimeMillis();
        boolean meaningful = hasMeaningful(changes);
        Path task = notify && meaningful ? tasks.write(current.fileName(), changes) : null;
        long taskMs = System.currentTimeMillis() - t2;
        String notice = notice(current, changes, task);
        lastNotice = notice;
        if (notify && meaningful && !notice.isBlank()) {
            telegram.sendAll(notice);
            for (String card : tasks.telegramCards(current.fileName(), changes)) {
                telegram.sendAll(card);
            }
        }
        metrics.recordCopilot(
                diffMs,
                impactMs,
                taskMs,
                SpecDiffService.summary(changes).getOrDefault("total", 0),
                (int) changes.stream().filter(TaskGenerationService::highImpact).count(),
                affectedFiles(changes),
                task == null ? 0 : 1);
        LOG.info("СП {} v{}: {} изменений, задача {}", current.fileName(), current.version(), changes.size(), task);
        return current;
    }

    public Overview overview() {
        String id = primaryDocumentId();
        if (id == null) {
            return Overview.empty();
        }
        return overview(id);
    }

    public Overview overview(String documentId) {
        SpecVersionSnap current = store.latest(documentId);
        if (current == null) {
            return Overview.empty();
        }
        SpecVersionSnap previous = store.previous(documentId);
        List<SpecChange> changes = store.changesOf(documentId);
        Map<String, Integer> summary = SpecDiffService.summary(changes);
        int front = 0;
        int back = 0;
        Set<String> files = new LinkedHashSet<>();
        for (SpecChange change : changes) {
            if (change.excluded()) {
                continue;
            }
            for (SpecChange.AffectedCode item : change.affected()) {
                files.add(item.path());
                if (CodeImpactService.frontend(item.repo(), item.path())) {
                    front += 1;
                } else {
                    back += 1;
                }
            }
        }
        return new Overview(
                current.documentId(),
                current.fileName(),
                previous == null ? current.version() : previous.version(),
                current.version(),
                current.createdInstant(),
                current.sections().size(),
                summary,
                front,
                back,
                files.size(),
                lastNotice,
                store.linksOf(documentId).size());
    }

    public List<SpecVersionSnap> versions(String documentId) {
        return store.lastTwo(documentId);
    }

    public List<SpecChange> changes(String documentId, boolean includeUnchanged) {
        List<SpecChange> items = store.changesOf(documentId);
        if (includeUnchanged) {
            return items;
        }
        return items.stream()
                .filter(change -> !"unchanged".equals(change.type()))
                .toList();
    }

    public SpecChange change(String documentId, String changeId) {
        return store.change(documentId, changeId);
    }

    public SpecChange exclude(String documentId, String changeId, String path, String symbol) {
        SpecChange change = store.change(documentId, changeId);
        if (change == null) {
            return null;
        }
        SpecChange next;
        if (path == null || path.isBlank()) {
            next = change.withExcluded(true);
        } else {
            List<SpecChange.AffectedCode> kept = change.affected().stream()
                    .filter(item -> !(path.equals(item.path())
                            && (symbol == null || symbol.isBlank() || symbol.equals(item.symbol()))))
                    .toList();
            next = change.withAffected(kept);
        }
        store.replaceChange(next);
        return next;
    }

    public SpecChange include(String documentId, String changeId) {
        SpecChange change = store.change(documentId, changeId);
        if (change == null) {
            return null;
        }
        SpecChange next = change.withExcluded(false);
        store.replaceChange(next);
        return next;
    }

    public List<RequirementCodeLink> links(String documentId) {
        return store.linksOf(documentId);
    }

    public String lastNotice() {
        return lastNotice;
    }

    public String primaryDocumentId() {
        List<String> ids = store.documentIds();
        if (ids.isEmpty()) {
            String file = configuredFile();
            return file.isBlank() ? null : SpecSectionParser.documentId(file);
        }
        String configured = SpecSectionParser.documentId(configuredFile());
        if (ids.contains(configured)) {
            return configured;
        }
        return ids.getFirst();
    }

    public synchronized Overview loadPair(String fileName, String oldText, String newText) {
        String name = fileName == null || fileName.isBlank() ? "petclinic-visits.md" : fileName.trim();
        String documentId = SpecSectionParser.documentId(name);
        store.clearDocument(documentId);
        store.save(new SpecVersionSnap(
                documentId, name, 1, fingerprint(oldText), Instant.now().minusSeconds(60).toString(),
                oldText, SpecSectionParser.parse(oldText)));
        store.save(new SpecVersionSnap(
                documentId, name, 2, fingerprint(newText), Instant.now().toString(),
                newText, SpecSectionParser.parse(newText)));
        analyze(documentId, true);
        return overview(documentId);
    }

    public synchronized String describeImpact(String documentId) {
        String id = documentId == null || documentId.isBlank() ? primaryDocumentId() : documentId;
        if (id == null) {
            return "СП не загружена. Загрузите старое и новое СП или #sp1/#sp2.";
        }
        SpecVersionSnap current = store.latest(id);
        SpecVersionSnap previous = store.previous(id);
        if (current == null) {
            return "Нет версий СП.";
        }
        if (previous == null) {
            return "Есть только одна версия СП («" + current.fileName() + "» v" + current.version()
                    + "). Загрузите новое СП, чтобы увидеть изменения.";
        }
        List<SpecChange> changes = changes(id, false);
        if (changes.isEmpty()) {
            return "Между v" + previous.version() + " и v" + current.version() + " значимых изменений нет.";
        }
        StringBuilder out = new StringBuilder();
        out.append("СП «").append(current.fileName()).append("»: v")
                .append(previous.version()).append(" > v").append(current.version()).append(".\n\n");
        out.append("Что изменилось:\n");
        for (SpecChange change : changes) {
            out.append("- [").append(change.significance()).append("] ")
                    .append(change.sectionPath()).append(": ")
                    .append(change.summary() == null ? "" : change.summary()).append('\n');
            out.append("  Было: ").append(clip(empty(change.oldBehavior(), change.oldText()), 220)).append('\n');
            out.append("  Стало: ").append(clip(empty(change.newBehavior(), change.newText()), 220)).append('\n');
            List<SpecChange.AffectedCode> front = change.affected() == null ? List.of() : change.affected().stream()
                    .filter(item -> CodeImpactService.frontend(item.repo(), item.path()))
                    .toList();
            List<SpecChange.AffectedCode> back = change.affected() == null ? List.of() : change.affected().stream()
                    .filter(item -> !CodeImpactService.frontend(item.repo(), item.path()))
                    .toList();
            out.append("  Frontend (что менять):\n");
            appendImpactLines(out, front);
            out.append("  Backend (что менять):\n");
            appendImpactLines(out, back);
            out.append('\n');
        }
        out.append("Дальше: #task создаст отдельные задания на front и back.");
        lastNotice = out.toString();
        telegram.sendAll(out.toString());
        return out.toString();
    }

    public synchronized String createFrontBackTasks(String documentId) {
        String id = documentId == null || documentId.isBlank() ? primaryDocumentId() : documentId;
        if (id == null) {
            return "СП ещё не загружена.";
        }
        SpecVersionSnap current = store.latest(id);
        if (current == null) {
            return "Нет версий СП.";
        }
        List<SpecChange> changes = changes(id, false);
        if (changes.isEmpty()) {
            return "Изменений нет. Сначала загрузите пару СП или #sp2, затем #sp.";
        }
        long t0 = System.currentTimeMillis();
        List<Path> written = tasks.writeFrontAndBack(current.fileName(), changes);
        long taskMs = System.currentTimeMillis() - t0;
        metrics.recordCopilot(
                0,
                0,
                taskMs,
                SpecDiffService.summary(changes).getOrDefault("total", 0),
                (int) changes.stream().filter(TaskGenerationService::highImpact).count(),
                affectedFiles(changes),
                written.size());
        if (written.isEmpty()) {
            return "Не удалось создать задачи: нет значимых изменений.";
        }
        List<String> cards = tasks.telegramCards(current.fileName(), changes);
        StringBuilder out = new StringBuilder("Созданы задания (front + back):\n\n");
        for (String card : cards) {
            out.append(card).append("\n\n");
            telegram.sendAll(card);
        }
        for (Path path : written) {
            out.append("файл: ").append(path.getFileName()).append('\n');
        }
        lastNotice = out.toString();
        return out.toString();
    }

    private static void appendImpactLines(StringBuilder out, List<SpecChange.AffectedCode> items) {
        if (items == null || items.isEmpty()) {
            out.append("    - уверенной связи не найдено\n");
            return;
        }
        for (SpecChange.AffectedCode item : items) {
            out.append("    - ").append(item.repo()).append(" `").append(item.path())
                    .append("` :: ").append(item.symbol())
                    .append(" - ").append(item.reason() == null ? "сверить с новой СП" : item.reason())
                    .append('\n');
        }
    }

    private static String empty(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback == null || fallback.isBlank() ? "нет текста" : fallback;
    }

    private static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    public synchronized String createTaskMessage(String documentId) {
        return createFrontBackTasks(documentId);
    }

    public synchronized Overview seedDemo() {
        String documentId = "petclinic-visits-md";
        String fileName = "petclinic-visits.md";
        store.save(new SpecVersionSnap(
                documentId, fileName, 1, fingerprint(DEMO_V1), Instant.now().minusSeconds(3600).toString(),
                DEMO_V1, SpecSectionParser.parse(DEMO_V1)));
        store.save(new SpecVersionSnap(
                documentId, fileName, 2, fingerprint(DEMO_V2), Instant.now().toString(),
                DEMO_V2, SpecSectionParser.parse(DEMO_V2)));
        analyze(documentId, true);
        return overview(documentId);
    }

    private String notice(SpecVersionSnap current, List<SpecChange> changes, Path task) {
        Map<String, Integer> summary = SpecDiffService.summary(changes);
        List<SpecChange> high = changes.stream().filter(TaskGenerationService::highImpact).toList();
        Set<String> repos = new LinkedHashSet<>();
        for (SpecChange change : changes) {
            for (SpecChange.AffectedCode item : change.affected()) {
                if (item.repo() != null && !item.repo().isBlank()) {
                    repos.add(item.repo());
                }
            }
        }
        StringBuilder out = new StringBuilder();
        out.append("СП «").append(current.fileName()).append("» обновлена.\n\n");
        out.append("Найдено:\n");
        out.append("критических: ").append(summary.getOrDefault("critical", 0)).append('\n');
        out.append("значимых: ").append(summary.getOrDefault("high", 0)).append('\n');
        out.append("средних: ").append(summary.getOrDefault("medium", 0)).append('\n');
        if (!high.isEmpty()) {
            SpecChange first = high.getFirst();
            out.append("\nКлючевое:\nРаздел ").append(first.sectionPath()).append(" - ").append(first.summary()).append('\n');
        }
        if (!repos.isEmpty()) {
            out.append("\nПотенциально затронуто:\n");
            for (String repo : repos) {
                out.append("- ").append(repo).append('\n');
            }
        }
        if (task != null) {
            out.append("\nСоздан draft задачи:\n").append(task.getFileName()).append('\n');
        }
        return out.toString();
    }

    private String siteUrl() {
        if (properties == null || properties.demo() == null || properties.demo().pto() == null) {
            return "https://dev.aisto.local";
        }
        return properties.demo().pto().url();
    }

    private String configuredFile() {
        if (properties == null || properties.specs() == null || properties.specs().file() == null) {
            return "";
        }
        return properties.specs().file().trim();
    }

    private static boolean hasMeaningful(List<SpecChange> changes) {
        if (changes == null || changes.isEmpty()) {
            return false;
        }
        return changes.stream().anyMatch(TaskGenerationService::actionable);
    }

    private static int affectedFiles(List<SpecChange> changes) {
        Set<String> files = new LinkedHashSet<>();
        for (SpecChange change : changes) {
            for (SpecChange.AffectedCode item : change.affected()) {
                if (item.path() != null && !item.path().isBlank()) {
                    files.add(item.path());
                }
            }
        }
        return files.size();
    }

    private static long fingerprint(String text) {
        return text == null ? 0 : text.hashCode() + (long) text.length() * 31;
    }

    private static final String DEMO_V1 = """
            3.2 Отмена и правка описания визита
            После создания визита владелец не может отменить визит.
            После создания визита описание визита нельзя редактировать.
            VisitService.canCancel и canEditDescription возвращают false.
            """;

    private static final String DEMO_V2 = """
            3.2 Отмена и правка описания визита
            Владелец может отменить визит, если до даты визита осталось не меньше 24 часов.
            Описание можно редактировать, пока дата визита в будущем.
            VisitService.canCancel / canEditDescription и VisitRow флаги canCancel / canEditDescription.
            """;

    public record Overview(
            String documentId,
            String fileName,
            int fromVersion,
            int toVersion,
            Instant updatedAt,
            int sections,
            Map<String, Integer> summary,
            int affectedFrontend,
            int affectedBackend,
            int affectedFiles,
            String lastNotice,
            int links
    ) {
        static Overview empty() {
            return new Overview("", "", 0, 0, null, 0, SpecDiffService.summary(List.of()), 0, 0, 0, "", 0);
        }
    }
}
