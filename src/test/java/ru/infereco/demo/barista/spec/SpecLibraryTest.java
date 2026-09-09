package ru.infereco.demo.barista.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import ru.infereco.demo.barista.config.MeetProperties;
import ru.infereco.demo.barista.knowledge.KnowledgeCatalog;
import ru.infereco.demo.barista.metrics.MetricsRegistry;

class SpecLibraryTest {

    @Test
    void acceptsOnlyConfiguredSpecFile() {
        MeetProperties properties = new MeetProperties(
                "t", "s", "d", "ds", "sp",
                null, null, null, null, null,
                null, null,
                new MeetProperties.Specs("/tmp", "/tmp", "карта.docx"),
                null, null, null);
        SpecLibrary library = new SpecLibrary(
                properties,
                new KnowledgeCatalog(new MetricsRegistry()),
                new TelegramNotifier(properties),
                new DevTaskWriter(properties),
                null);

        assertThat(library.accepted(Path.of("/tmp/карта.docx"))).isTrue();
        assertThat(library.accepted(Path.of("/tmp/другая.docx"))).isFalse();
        assertThat(library.accepted(Path.of("/tmp/карта.txt"))).isFalse();
    }
}
