package ru.infereco.demo.barista.skill;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SkillRouterTest {

    @Test
    void routesTopSpHashAndPetClinicCues() {
        SkillCatalog catalog = new SkillCatalog(null);
        SkillRouter router = new SkillRouter(catalog);

        assertThat(router.route(false, "#hint куда кликнуть", ""))
                .isEqualTo("hint");
        assertThat(router.route(false, "#task", ""))
                .isEqualTo("topsp");
        assertThat(router.route(false, "просто привет", ""))
                .isEqualTo("topsp");
        assertThat(router.route(false, "что с visit и canCancel в petclinic", ""))
                .isEqualTo("topsp");
        assertThat(router.route(false, "вопрос на собеседовании в zoom", ""))
                .isEqualTo("interview");
        assertThat(router.route(true, "карта учреждений", "tvorchestvo"))
                .isEqualTo("doom");
        assertThat(router.route(false, "что ответить кандидату в zoom", "слайд с вопросом", "interview"))
                .isEqualTo("interview");
    }

    @Test
    void fillsPlaceholdersWhenDemoPresent() {
        Skill raw = SkillCatalog.parse("demo.md", """
                ---
                id: demo
                title: Demo
                triggers: demo
                ---
                URL {{pto.url}} логин {{pto.login}}
                """);
        var properties = new ru.infereco.demo.barista.config.MeetProperties(
                "t", "s", "d", "ds", "sp",
                null, null, null, null, null,
                new ru.infereco.demo.barista.config.MeetProperties.Demo(
                        new ru.infereco.demo.barista.config.MeetProperties.Pto(
                                "http://localhost:8080", "http://localhost:8080/api", "demo", "secret")),
                null, null, null, null, null);
        Skill filled = SkillCatalog.fill(raw, properties);
        assertThat(filled.body()).contains("http://localhost:8080", "demo");
    }
}
