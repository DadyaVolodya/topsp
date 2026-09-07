package ru.infereco.demo.barista.pto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class PtoPlacesTest {

    @Test
    void picksParentCampusAndBuildsYandexRoute() throws Exception {
        String json = """
                {"content":[
                  {"id":"11111111-1111-1111-1111-111111111111","parentId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                   "nameShort":"филиал","addressFull":"Филевская 29","lat":55.74,"lon":37.48,
                   "directions":["MUSICAL_INSTRUMENTS"]},
                  {"id":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","parentId":null,
                   "nameShort":"МССМШ им. Гнесиных","addressFull":"улица Знаменка, д. 12/2, стр. 3",
                   "lat":55.750803,"lon":37.605068,"directions":["MUSICAL_INSTRUMENTS","THEATRE"]}
                ]}
                """;
        PtoQuery query = PtoQuery.parse("как добраться до школы гнесиных");
        var place = PtoPlaces.fromSearch(new ObjectMapper().readTree(json), "https://stand.example/prefix", query)
                .orElseThrow();
        assertThat(place.id()).isEqualTo("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        assertThat(place.address()).contains("Знаменка");
        assertThat(place.cardUrl())
                .isEqualTo("https://stand.example/prefix/institutions/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        assertThat(place.routeToPlaceUrl()).isEqualTo("https://yandex.ru/maps/?rtext=~55.750803,37.605068");
        assertThat(place.openUrl()).isEqualTo(place.routeToPlaceUrl());
        assertThat(place.cardUrl()).startsWith("https://stand.example/prefix/institutions/");
        assertThat(place.directionLabels()).contains("Музыкальные инструменты");
    }
}
