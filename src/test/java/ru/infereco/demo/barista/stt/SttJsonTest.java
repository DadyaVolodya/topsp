package ru.infereco.demo.barista.stt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SttJsonTest {

    @Test
    void readsPartialAndAlternatives() {
        assertThat(SttJson.partial("{\"partial\":\"стенда\"}")).isEqualTo("стенда");
        assertThat(SttJson.alternatives("""
                {"alternatives":[{"confidence":0.2,"text":"стендаб"},{"confidence":0.9,"text":"стендап"}]}
                """)).hasSize(2);
        assertThat(SttJson.alternatives("""
                {"alternatives":[{"confidence":0.2,"text":"стендаб"},{"confidence":0.9,"text":"стендап"}]}
                """).getFirst().text()).isEqualTo("стендап");
    }
}
