package ru.infereco.demo.barista.pto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PtoPagesTest {

    @Test
    void opensInstitutionsMapOnAisto() {
        var page = PtoPages.resolve("открой карту организаций на aisto", "", "https://dev.aisto.local")
                .orElseThrow();
        assertThat(page.path()).isEqualTo("/institutions");
        assertThat(page.openUrl()).isEqualTo("https://dev.aisto.local/institutions");
    }

    @Test
    void opensLoginAndDirectionFromFrontRoutes() {
        assertThat(PtoPages.resolve("открой логин пто", "", "https://dev.aisto.local").orElseThrow().path())
                .isEqualTo("/login");
        assertThat(PtoPages.resolve("покажи рисунок и живопись на aisto", "", "https://dev.aisto.local")
                .orElseThrow()
                .path())
                .isEqualTo("/institutions?direction=DRAWING_PAINTING");
    }

    @Test
    void leavesSchoolSearchToApi() {
        assertThat(PtoPages.resolve("как доехать до несенных покажи маршрут", "", "https://dev.aisto.local"))
                .isEmpty();
    }

    @Test
    void ignoresInterview() {
        assertThat(PtoPages.resolve("вопрос кандидату в zoom", "слайд", "https://dev.aisto.local"))
                .isEmpty();
    }

    @Test
    void derivesApiFromSite() {
        assertThat(PtoPages.apiUrl("https://dev.aisto.local", null)).isEqualTo("https://dev.aisto.local/api");
        assertThat(PtoPages.apiUrl("https://dev.aisto.local", "https://dev.aisto.local/api"))
                .isEqualTo("https://dev.aisto.local/api");
    }
}
