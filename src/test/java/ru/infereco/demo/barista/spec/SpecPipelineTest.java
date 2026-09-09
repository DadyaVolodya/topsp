package ru.infereco.demo.barista.spec;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.infereco.demo.barista.chat.InferecoClient;
import ru.infereco.demo.barista.code.CodeImpactService;
import ru.infereco.demo.barista.code.CodeLibrary;
import ru.infereco.demo.barista.config.MeetProperties;
import ru.infereco.demo.barista.knowledge.KnowledgeCatalog;
import ru.infereco.demo.barista.metrics.MetricsRegistry;

class SpecPipelineTest {

    @Test
    void firstLoadIsVersionOneWithoutTask(@TempDir Path dir) {
        SpecPipeline pipeline = pipeline(dir);
        SpecVersionSnap v1 = pipeline.onSpecLoaded("заявка.md", "1. Раздел\nЧерновик можно править.", false);
        assertThat(v1.version()).isEqualTo(1);
        assertThat(pipeline.changes(v1.documentId(), false)).isEmpty();
        assertThat(new DevTaskWriter(props(dir)).recent(5)).isEmpty();
    }

    @Test
    void secondLoadCreatesDiffTaskAndOverview(@TempDir Path dir) {
        SpecPipeline pipeline = pipeline(dir);
        pipeline.onSpecLoaded("заявка.md", """
                4.2 Редактирование заявки
                После подачи заявки пользователь не может редактировать поля заявки.
                """, false);
        pipeline.onSpecLoaded("заявка.md", """
                4.2 Редактирование заявки
                После подачи заявки пользователь может редактировать поля до начала проверки.
                """, true);
        String id = SpecSectionParser.documentId("заявка.md");
        assertThat(pipeline.versions(id)).hasSize(2);
        assertThat(pipeline.changes(id, false)).isNotEmpty();
        SpecPipeline.Overview overview = pipeline.overview(id);
        assertThat(overview.fromVersion()).isEqualTo(1);
        assertThat(overview.toVersion()).isEqualTo(2);
        assertThat(overview.summary().get("total")).isGreaterThan(0);
        assertThat(new DevTaskWriter(props(dir)).recent(5)).isNotEmpty();
        assertThat(pipeline.lastNotice()).contains("обновлена");
    }

    @Test
    void demoSeedProducesTwoVersions(@TempDir Path dir) {
        SpecPipeline pipeline = pipeline(dir);
        SpecPipeline.Overview overview = pipeline.seedDemo();
        assertThat(overview.documentId()).isEqualTo("demo-application-edit");
        assertThat(overview.toVersion()).isEqualTo(2);
        assertThat(pipeline.changes("demo-application-edit", false)).isNotEmpty();
    }

    private static SpecPipeline pipeline(Path dir) {
        MeetProperties properties = props(dir);
        MetricsRegistry metrics = new MetricsRegistry();
        InferecoClient client = new InferecoClient("", "http://127.0.0.1", properties);
        SpecVersionStore store = new SpecVersionStore(dir.resolve("versions"), new ObjectMapper());
        return new SpecPipeline(
                store,
                new SpecDiffService(client, properties),
                new CodeImpactService(new CodeLibrary(properties, new KnowledgeCatalog(metrics)), client, properties),
                new TaskGenerationService(new DevTaskWriter(properties)),
                new TelegramNotifier(properties),
                metrics,
                properties);
    }

    private static MeetProperties props(Path dir) {
        return new MeetProperties(
                "t", "s", "d", "ds", "sp",
                new MeetProperties.Models("m", "c", "glm-5.3", "v"),
                null, null, null, null,
                new MeetProperties.Demo(new MeetProperties.Pto("https://dev.aisto.local", "", "", "")),
                null,
                new MeetProperties.Specs(dir.toString(), dir.toString(), "заявка.md"),
                new MeetProperties.Telegram("", ""),
                null,
                null);
    }
}
