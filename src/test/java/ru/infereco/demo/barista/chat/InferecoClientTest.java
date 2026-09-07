package ru.infereco.demo.barista.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class InferecoClientTest {

    @Test
    void readsArrayContentParts() throws Exception {
        var root = new ObjectMapper().readTree("""
                {"choices":[{"message":{"content":[{"type":"text","text":"На слайде релиз."}]}}]}
                """);
        assertThat(InferecoClient.textOf(root.path("choices").path(0).path("message").path("content")))
                .isEqualTo("На слайде релиз.");
    }
}
