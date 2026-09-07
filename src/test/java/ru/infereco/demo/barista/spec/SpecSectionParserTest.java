package ru.infereco.demo.barista.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SpecSectionParserTest {

    @Test
    void splitsNumberedHeadings() {
        List<SpecSection> sections = SpecSectionParser.parse("""
                4. Подача заявки
                Пользователь заполняет форму.

                4.2 Редактирование заявки
                После подачи нельзя менять поля.
                """);

        assertThat(sections).hasSize(2);
        assertThat(sections.getFirst().sectionPath()).isEqualTo("4");
        assertThat(sections.get(1).sectionPath()).isEqualTo("4.2");
        assertThat(sections.get(1).heading()).contains("Редактирование");
        assertThat(sections.get(1).hash()).isNotBlank();
    }

    @Test
    void documentIdIsStableSlug() {
        assertThat(SpecSectionParser.documentId("Системная постановка.doc"))
                .isEqualTo("системная-постановка-doc");
    }

    @Test
    void mergeKeepsLongestBodyForDuplicatePath() {
        List<SpecSection> sections = SpecSectionParser.parse("""
                5.11. Реестр FAQ
                кратко

                5.12. Другое
                x

                5.11. Реестр FAQ
                Полный текст: статусы черновик и опубликовано, кнопка Опубликовать.
                """);
        assertThat(sections.stream().filter(s -> "5.11".equals(s.sectionPath()))).hasSize(1);
        assertThat(sections.stream().filter(s -> "5.11".equals(s.sectionPath())).findFirst().orElseThrow().text())
                .contains("Опубликовать");
    }
}
