package ru.infereco.demo.barista.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConversationStoreTest {

    @Test
    void createsAndReturnsSession() {
        ConversationStore store = new ConversationStore();
        ChatSession session = store.create();
        session.add("user", "привет", "text", null);

        ChatSession found = store.require(session.id());

        assertThat(found.messages()).hasSize(1);
        assertThat(found.messages().getFirst().text()).isEqualTo("привет");
    }

    @Test
    void missingSessionThrows() {
        ConversationStore store = new ConversationStore();
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> store.require(id)).isInstanceOf(SessionNotFoundException.class);
    }
}
