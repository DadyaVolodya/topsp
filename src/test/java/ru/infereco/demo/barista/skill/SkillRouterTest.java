package ru.infereco.demo.barista.skill;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SkillRouterTest {

    @Test
    void routesPtoFromMapAndLoginCues() {
        SkillCatalog catalog = new SkillCatalog(null);
        SkillRouter router = new SkillRouter(catalog);

        assertThat(router.route(false, "открой tvorchestvo карту учреждений и маршрут", ""))
                .isEqualTo("pto-site");
        assertThat(router.route(false, "открой карту организаций на aisto", ""))
                .isEqualTo("pto-site");
        assertThat(router.route(false, "как добрать до школы гнесенных", ""))
                .isEqualTo("pto-site");
        assertThat(router.route(false, "как доехать до несенных покажи маршрут", ""))
                .isEqualTo("pto-site");
        assertThat(router.route(false, "вопрос на собеседовании в zoom", ""))
                .isEqualTo("interview");
        assertThat(router.route(true, "карта учреждений", "tvorchestvo"))
                .isEqualTo("doom");
        assertThat(router.route(false, "кандидат спрашивает про опыт в zoom", ""))
                .isEqualTo("interview");
        assertThat(router.route(false, "дальше", "tvorchestvo карта учебных заведений", "pto-site"))
                .isEqualTo("pto-site");
        assertThat(router.route(false, "что ответить кандидату в zoom", "слайд с вопросом", "interview"))
                .isEqualTo("interview");
    }

    @Test
    void fillsPtoPlaceholdersFromDemoConfig() {
        Skill raw = SkillCatalog.parse("pto-site.md", """
                ---
                id: pto-site
                title: ПТО
                triggers: карта, маршрут
                ---
                URL {{pto.url}} логин {{pto.login}} пароль {{pto.password}}
                """);
        var properties = new ru.infereco.demo.barista.config.MeetProperties(
                "t", "s", "d", "ds", "sp",
                null, null, null, null, null,
                new ru.infereco.demo.barista.config.MeetProperties.Demo(
                        new ru.infereco.demo.barista.config.MeetProperties.Pto(
                                "https://dev.aisto.local", "https://dev.aisto.local/api", "pto-pp", "secret")),
                null, null, null, null);
        Skill filled = SkillCatalog.fill(raw, properties);
        assertThat(filled.body()).contains("https://dev.aisto.local", "pto-pp", "secret");
    }
}
