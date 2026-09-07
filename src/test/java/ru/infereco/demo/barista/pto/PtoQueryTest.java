package ru.infereco.demo.barista.pto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PtoQueryTest {

    @Test
    void extractsGnesinsTypoAndWantsRoute() {
        PtoQuery query = PtoQuery.parse("как добрать до школы гнесенных");
        assertThat(query.term()).isEqualTo("Гнесин");
        assertThat(query.wantsRoute()).isTrue();
        assertThat(query.wantsRedSquare()).isFalse();
        assertThat(query.searchable()).isTrue();
    }

    @Test
    void extractsSttNesennykhAsGnesinsRoute() {
        PtoQuery query = PtoQuery.parse("как доехать до несенных покажи маршрут на сайте пто");
        assertThat(query.term()).isEqualTo("Гнесин");
        assertThat(query.wantsRoute()).isTrue();
        assertThat(query.searchable()).isTrue();
    }

    @Test
    void routeAloneIsNotSearchableUntilScreenNamesSchool() {
        assertThat(PtoQuery.parse("покажи маршрут").searchable()).isFalse();
        assertThat(PtoQuery.parse("открой карту организаций на aisto").searchable()).isFalse();
    }

    @Test
    void usesScreenBriefWhenUserOnlyAskedForRoute() {
        PtoQuery query = PtoQuery.parse(
                "покажи маршрут",
                "На сайте tvorchestvo карточка МССМШ им. Гнесиных, Знаменка, кнопка Маршрут");
        assertThat(query.term()).isEqualTo("Гнесин");
        assertThat(query.wantsRoute()).isTrue();
        assertThat(query.searchable()).isTrue();
    }

    @Test
    void ignoresLongVisionDumpWithoutSchoolName() {
        PtoQuery query = PtoQuery.parse(
                "что это",
                "На слайде таблица спринта, колонка владелец пустая, внизу кнопка далее");
        assertThat(query.searchable()).isFalse();
    }

    @Test
    void ignoresInterviewSmalltalk() {
        PtoQuery query = PtoQuery.parse("расскажи про опыт в команде");
        assertThat(query.searchable()).isFalse();
    }

    @Test
    void redSquareFromSchool() {
        PtoQuery query = PtoQuery.parse("маршрут от гнесиных до красной площади");
        assertThat(query.term()).isEqualTo("Гнесин");
        assertThat(query.wantsRedSquare()).isTrue();
        assertThat(query.wantsRoute()).isTrue();
    }
}
