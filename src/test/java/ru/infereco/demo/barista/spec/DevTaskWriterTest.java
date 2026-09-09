package ru.infereco.demo.barista.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.infereco.demo.barista.config.MeetProperties;

class DevTaskWriterTest {

    @Test
    void writesMarkdownTaskForSpecChange(@TempDir Path dir) throws Exception {
        MeetProperties properties = new MeetProperties(
                "t", "s", "d", "ds", "sp",
                null, null, null, null, null,
                null, null,
                new MeetProperties.Specs(dir.resolve("sp").toString(), dir.toString(), null),
                null, null, null);
        DevTaskWriter writer = new DevTaskWriter(properties);

        Path file = writer.write("карта.docx", "СП изменена", "Поиск по названию.");

        assertThat(file).isNotNull();
        assertThat(Files.readString(file)).contains("карта.docx", "СП изменена", "Поиск по названию.");
        assertThat(writer.recent(5)).isNotEmpty();
    }
}
