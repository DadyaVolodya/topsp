package ru.infereco.demo.barista.improve;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CritiqueParseTest {

    @Test
    void parsesJsonFromModelWrapper() {
        String raw = """
                вот оценка
                {"quality":8.5,"keep":true,"revise":false,"title":"нет владельца","fact":"Задачу без имени не фиксировать","reason":"конкретно"}
                """;
        SelfImproveService.Critique critique = SelfImproveService.Critique.parse(raw);
        assertThat(critique.quality()).isEqualTo(8.5);
        assertThat(critique.keep()).isTrue();
        assertThat(critique.revise()).isFalse();
        assertThat(critique.title()).isEqualTo("нет владельца");
        assertThat(critique.fact()).contains("имени");
    }
}
