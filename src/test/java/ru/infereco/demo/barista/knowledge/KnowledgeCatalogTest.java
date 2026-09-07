package ru.infereco.demo.barista.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ru.infereco.demo.barista.metrics.MetricsRegistry;

class KnowledgeCatalogTest {

    @Test
    void learnedFactsAreAlwaysRetrieved() {
        KnowledgeCatalog catalog = new KnowledgeCatalog(new MetricsRegistry());
        catalog.learn("нет владельца", "Задачу без имени в протокол не писать.");

        var retrieved = catalog.retrieve("как фиксировать задачи", 3);

        assertThat(retrieved.stream().anyMatch(d -> "learned".equals(d.source()))).isTrue();
        assertThat(catalog.all()).anyMatch(d -> d.id().contains("agenda") || d.id().contains("e1m1") || d.id().contains("pto"));
    }

    @Test
    void ingestTxtAddsUploadChunksToRag() {
        KnowledgeCatalog catalog = new KnowledgeCatalog(new MetricsRegistry());
        catalog.ingestTxt("rules.txt", "Секретарь пишет решения только в Notion.\n\nБез записи решение недействительно.".getBytes());

        var retrieved = catalog.retrieve("Notion секретарь", 3);

        assertThat(retrieved.stream().anyMatch(d -> "upload".equals(d.source()))).isTrue();
        assertThat(catalog.all()).anyMatch(d -> "upload".equals(d.source()) && d.title().contains("rules.txt"));
    }

    @Test
    void specChunksAreRetrievedForPtoQuery() {
        KnowledgeCatalog catalog = new KnowledgeCatalog(new MetricsRegistry());
        catalog.replaceSpec("СП карта.docx", "Поиск по названию учебного заведения на карте ПТО.");
        var retrieved = catalog.retrieve("поиск по названию карта пто", 3);
        assertThat(retrieved.stream().anyMatch(d -> "spec".equals(d.source()))).isTrue();
        assertThat(catalog.countBySource("spec")).isGreaterThan(0);
    }

    @Test
    void groupsSpecChunksByFile() {
        KnowledgeCatalog catalog = new KnowledgeCatalog(new MetricsRegistry());
        catalog.replaceSpec("карта.docx", "А".repeat(2000));
        var groups = catalog.groups().stream().filter(g -> "spec".equals(g.source())).toList();
        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().title()).isEqualTo("карта.docx");
        assertThat(groups.getFirst().chunks()).isGreaterThan(1);
    }

    @Test
    void codeChunksAreRetrievedForRouteQuery() {
        KnowledgeCatalog catalog = new KnowledgeCatalog(new MetricsRegistry());
        catalog.replaceCode("aisto-front/src/lib/app-routes.ts", "export const APP_ROUTES = { grants: '/grant-program' };");
        var retrieved = catalog.retrieve("grant-program app-routes", 3);
        assertThat(retrieved.stream().anyMatch(d -> "code".equals(d.source()))).isTrue();
    }
}
