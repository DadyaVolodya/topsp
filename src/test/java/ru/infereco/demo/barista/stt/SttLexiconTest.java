package ru.infereco.demo.barista.stt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SttLexiconTest {

    @Test
    void correctsNearDomainWord() {
        assertThat(SttLexicon.best("стендаб", Set.of("стендап", "ретро"))).isEqualTo("стендап");
        assertThat(SttLexicon.best("план", Set.of("стендап", "план"))).isEqualTo("план");
    }

    @Test
    void picksHypothesisWithMoreLexiconHits() {
        SttLexicon lexicon = new SttLexicon(new ru.infereco.demo.barista.knowledge.KnowledgeCatalog(
                new ru.infereco.demo.barista.metrics.MetricsRegistry()));
        String picked = lexicon.pick(List.of(
                new SttJson.Hypothesis("стендаб без имени", 0.4),
                new SttJson.Hypothesis("стендап без имени", 0.5)
        ));
        assertThat(picked).contains("стендап");
    }
}
