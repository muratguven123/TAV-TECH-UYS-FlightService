package com.tav.FlightService.config;

// FIX: TD-011 — RedisKeys sabitlerinin doğruluğunu doğrula.
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RedisKeys — Redis anahtar üretim testleri")
class RedisKeysTest {

    @Test
    @DisplayName("FLIGHTS_SORTED sabiti doğru değeri taşır")
    void flightsSorted_isCorrect() {
        assertThat(RedisKeys.FLIGHTS_SORTED).isEqualTo("flights:sorted");
    }

    @Test
    @DisplayName("REFERENCE_PREFIX sabiti doğru prefix taşır")
    void referencePrefix_isCorrect() {
        assertThat(RedisKeys.REFERENCE_PREFIX).isEqualTo("reference:");
    }

    @ParameterizedTest(name = "airline({0}) = {1}")
    @CsvSource({
        "TK,  reference:AIRLINE:TK",
        "LH,  reference:AIRLINE:LH",
        "AA,  reference:AIRLINE:AA"
    })
    @DisplayName("airline() doğru anahtar üretir")
    void airline_producesCorrectKey(String iata, String expected) {
        assertThat(RedisKeys.airline(iata.trim())).isEqualTo(expected.trim());
    }

    @ParameterizedTest(name = "aircraft({0}) = {1}")
    @CsvSource({
        "TC-JFA, reference:AIRCRAFT:TC-JFA",
        "TC-LKM, reference:AIRCRAFT:TC-LKM"
    })
    @DisplayName("aircraft() doğru anahtar üretir")
    void aircraft_producesCorrectKey(String tail, String expected) {
        assertThat(RedisKeys.aircraft(tail.trim())).isEqualTo(expected.trim());
    }

    @ParameterizedTest(name = "station({0}) = {1}")
    @CsvSource({
        "LTFM, reference:STATION:LTFM",
        "LTAC, reference:STATION:LTAC"
    })
    @DisplayName("station() doğru anahtar üretir")
    void station_producesCorrectKey(String icao, String expected) {
        assertThat(RedisKeys.station(icao.trim())).isEqualTo(expected.trim());
    }

    @ParameterizedTest(name = "route({0},{1}) = {2}")
    @CsvSource({
        "LTFM, LTAC, reference:ROUTE:LTFM-LTAC",
        "LTAC, LTFM, reference:ROUTE:LTAC-LTFM",
        "EDDF, EGLL, reference:ROUTE:EDDF-EGLL"
    })
    @DisplayName("route() doğru anahtar üretir")
    void route_producesCorrectKey(String origin, String dest, String expected) {
        assertThat(RedisKeys.route(origin.trim(), dest.trim())).isEqualTo(expected.trim());
    }

    // ------------------------------------------------------------------ FIX: TD-011 — fromEvent

    @Test
    @DisplayName("fromEvent() ROUTE businessKey ile route() çıktısıyla birebir eşleşir (cache invalidation garantisi)")
    void fromEvent_matchesValidatorFormat_forRoute() {
        // FIX: TD-011 — kritik: bu test başarısız olursa cache invalidation kırılır
        // RouteService.buildRouteKey() → "LTFM-LTAC" formatında businessKey üretir
        assertThat(RedisKeys.fromEvent("ROUTE", "LTFM-LTAC"))
                .isEqualTo(RedisKeys.route("LTFM", "LTAC"));
    }

    @Test
    @DisplayName("fromEvent() AIRLINE businessKey ile airline() çıktısıyla eşleşir")
    void fromEvent_matchesValidatorFormat_forAirline() {
        assertThat(RedisKeys.fromEvent("AIRLINE", "TK"))
                .isEqualTo(RedisKeys.airline("TK"));
    }

    @Test
    @DisplayName("fromEvent() STATION businessKey ile station() çıktısıyla eşleşir")
    void fromEvent_matchesValidatorFormat_forStation() {
        assertThat(RedisKeys.fromEvent("STATION", "LTFM"))
                .isEqualTo(RedisKeys.station("LTFM"));
    }

    @Test
    @DisplayName("fromEvent() AIRCRAFT businessKey ile aircraft() çıktısıyla eşleşir")
    void fromEvent_matchesValidatorFormat_forAircraft() {
        assertThat(RedisKeys.fromEvent("AIRCRAFT", "TC-JFA"))
                .isEqualTo(RedisKeys.aircraft("TC-JFA"));
    }
}
