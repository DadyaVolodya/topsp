package ru.infereco.demo.barista.spec;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import ru.infereco.demo.barista.code.CodeChunk;
import ru.infereco.demo.barista.code.CodeLibrary;

/**
 * Жёсткая преамбула продукта для LLM: маленький PetClinic-контекст вместо «свободного» бреда.
 */
@Component
public class ProductBrief {

    private final SpecPipeline pipeline;
    private final CodeLibrary code;

    public ProductBrief(SpecPipeline pipeline, CodeLibrary code) {
        this.pipeline = pipeline;
        this.code = code;
    }

    public String forTopSp(String userText) {
        StringBuilder out = new StringBuilder();
        out.append("""
                === PRODUCT CARD (истина, не выдумывай вне этого блока) ===
                Продукт: Spring PetClinic (демо-срез TopSP).
                Домен: Owner, Pet, Visit.
                Backend repo: petclinic-back. Frontend repo: petclinic-front.
                Ключевые символы:
                - VisitController /api/pets/{petId}/visits (GET, POST, PUT, DELETE)
                - VisitService.canCancel, VisitService.canEditDescription
                - PetVisitsPage, VisitRow (canCancel, canEditDescription)
                - visitsApi.cancelVisit, visitsApi.updateVisitDescription
                Карта UI: /owners/{id} > pet > visits.
                Формат ответа: 1) суть изменения/факта 2) потенциально затронутый код 3) один следующий шаг.
                Если факта нет в СП/коде/overview - так и скажи, не додумывай Aisto/ПТО/гранты.
                """);
        SpecPipeline.Overview overview = pipeline.overview();
        if (overview != null && overview.documentId() != null && !overview.documentId().isBlank()) {
            out.append("СП: ").append(overview.fileName())
                    .append(" v").append(overview.fromVersion()).append(" > v").append(overview.toVersion())
                    .append(", секций ").append(overview.sections())
                    .append(", изменений ")
                    .append(overview.summary() == null ? 0 : overview.summary().getOrDefault("total", 0))
                    .append(", front/back сигналов ")
                    .append(overview.affectedFrontend()).append("/").append(overview.affectedBackend())
                    .append('\n');
            if (overview.lastNotice() != null && !overview.lastNotice().isBlank()) {
                out.append("Последний notice:\n").append(clip(overview.lastNotice(), 900)).append('\n');
            }
            List<SpecChange> all = pipeline.changes(overview.documentId(), false);
            List<SpecChange> changes = pipeline.latestChanges(overview.documentId(), false);
            if (!all.isEmpty()) {
                out.append("История изменений: ").append(all.size())
                        .append(", в последней паре версий: ").append(changes.size()).append(".\n");
            }
            if (!changes.isEmpty()) {
                out.append("Изменения последней пары (top):\n");
                for (SpecChange change : changes.stream().limit(4).toList()) {
                    out.append("- [").append(change.significance()).append("] ")
                            .append(change.sectionPath()).append(": ")
                            .append(empty(change.summary())).append('\n');
                    out.append("  было: ").append(clip(empty(change.oldBehavior()), 160)).append('\n');
                    out.append("  стало: ").append(clip(empty(change.newBehavior()), 160)).append('\n');
                    if (change.affected() != null && !change.affected().isEmpty()) {
                        String files = change.affected().stream()
                                .map(a -> a.repo() + ":" + a.symbol())
                                .collect(Collectors.joining(", "));
                        out.append("  impact: ").append(files).append('\n');
                    }
                }
            }
        } else {
            out.append("СП ещё не загружена. Команды: #sp1 затем #sp2, либо upload .md.\n");
        }
        String q = userText == null || userText.isBlank() ? "visit cancel description PetVisitsPage VisitService" : userText;
        List<CodeChunk> hits = code.search(q, "", 6);
        if (!hits.isEmpty()) {
            out.append("Код (кандидаты):\n");
            for (CodeChunk chunk : hits) {
                out.append("- ").append(chunk.repo()).append(" ")
                        .append(chunk.path()).append(" :: ")
                        .append(chunk.symbol()).append(" [").append(chunk.symbolType()).append("]\n");
            }
        }
        out.append("=== END PRODUCT CARD ===\n");
        return out.toString();
    }

    private static String empty(String value) {
                return value == null || value.isBlank() ? "нет" : value;
    }

    private static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
