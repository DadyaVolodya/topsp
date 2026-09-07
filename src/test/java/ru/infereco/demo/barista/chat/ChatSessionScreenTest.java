package ru.infereco.demo.barista.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatSessionScreenTest {

    @Test
    void remembersAndDedupsScreenBrief() {
        ChatSession session = new ChatSession(UUID.randomUUID(), Instant.now());
        session.rememberScreen("На слайде цель спринта, владельца нет.");
        assertThat(session.hasScreen()).isTrue();
        assertThat(session.sameScreen("на слайде цель спринта, владельца нет.")).isTrue();
        assertThat(session.sameScreen("в IDE красная ошибка NullPointer")).isFalse();
        session.setSkill("pto-site");
        assertThat(session.skill()).isEqualTo("pto-site");
        session.setMode("doom");
        session.setSkill("interview");
        assertThat(session.skill()).isEqualTo("doom");
    }
}
