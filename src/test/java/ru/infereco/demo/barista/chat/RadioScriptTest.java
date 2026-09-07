package ru.infereco.demo.barista.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RadioScriptTest {

    @Test
    void earlyHintMentionsArmor() {
        RadioScript script = new RadioScript();
        assertThat(script.line(5, "нет")).containsIgnoringCase("брон");
    }

    @Test
    void lateHintOffersCheat() {
        RadioScript script = new RadioScript();
        assertThat(script.line(200, "нет")).containsAnyOf("idclip", "iddqd", "idkfa");
    }
}
