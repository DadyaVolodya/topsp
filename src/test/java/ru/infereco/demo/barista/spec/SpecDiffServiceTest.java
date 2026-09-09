package ru.infereco.demo.barista.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.infereco.demo.barista.chat.InferecoClient;
import ru.infereco.demo.barista.config.MeetProperties;

class SpecDiffServiceTest {

    @Test
    void sameHashIsUnchangedAndSkipped() {
        List<SpecSection> sections = SpecSectionParser.parse("1. Раздел\nОдин и тот же текст.");
        SpecVersionSnap v1 = snap(1, sections);
        SpecVersionSnap v2 = snap(2, sections);
        List<SpecChange> changes = service().diff(v1, v2, "https://dev.aisto.local");
        assertThat(changes).isEmpty();
    }

    @Test
    void editAfterSubmitIsHighImpact() {
        SpecVersionSnap v1 = snap(1, SpecSectionParser.parse("""
                4.2 Редактирование заявки
                После подачи заявки пользователь не может редактировать поля заявки.
                """));
        SpecVersionSnap v2 = snap(2, SpecSectionParser.parse("""
                4.2 Редактирование заявки
                После подачи заявки пользователь может редактировать поля до начала проверки.
                """));
        List<SpecChange> changes = service().diff(v1, v2, "https://dev.aisto.local");
        assertThat(changes).isNotEmpty();
        SpecChange change = changes.getFirst();
        assertThat(change.type()).isIn("modified", "moved");
        assertThat(change.significance()).isEqualTo("high");
        assertThat(change.requiresCodeChange()).isTrue();
        assertThat(change.sectionPath()).isEqualTo("4.2");
        assertThat(change.navigation().route()).isEqualTo("/grant-program");
        assertThat(change.navigation().action()).isEqualTo("highlight");
    }

    @Test
    void addedSectionIsDetected() {
        SpecVersionSnap v1 = snap(1, SpecSectionParser.parse("1. Общее\nТекст."));
        SpecVersionSnap v2 = snap(2, SpecSectionParser.parse("1. Общее\nТекст.\n\n2. Новое требование\nПользователь обязан подтвердить email."));
        List<SpecChange> changes = service().diff(v1, v2, "https://dev.aisto.local");
        assertThat(changes).anyMatch(change -> "added".equals(change.type()));
    }

    private static SpecDiffService service() {
        return new SpecDiffService(new InferecoClient("", "http://127.0.0.1", props()), props());
    }

    private static SpecVersionSnap snap(int version, List<SpecSection> sections) {
        return new SpecVersionSnap("demo", "demo.md", version, version, Instant.now().toString(), "raw", sections);
    }

    private static MeetProperties props() {
        return new MeetProperties(
                "t", "s", "d", "ds", "sp",
                new MeetProperties.Models("m", "c", "glm-5.3", "v"),
                null, null, null, null, null, null,
                new MeetProperties.Specs("/tmp", "/tmp", "demo.md"),
                null, null, null);
    }
}
