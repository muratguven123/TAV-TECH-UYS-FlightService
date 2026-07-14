package com.tav.FlightService.validator;

import com.tav.FlightService.cache.CachingReferenceValidator;
import com.tav.FlightService.client.ReferenceManagerClient;
import com.tav.FlightService.client.dto.AircraftDto;
import com.tav.FlightService.client.dto.AirlineDto;
import com.tav.FlightService.client.dto.RouteDto;
import com.tav.FlightService.client.dto.StationDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;

import static com.tav.FlightService.cache.CachingReferenceValidator.POSITIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.AfterEach;
import org.mockito.MockitoAnnotations;

class CachingReferenceValidatorTest {

    @Mock StringRedisTemplate stringRedisTemplate;
    @Mock ValueOperations<String, String> valueOperations;
    @Mock ReferenceManagerClient referenceManagerClient;

    CachingReferenceValidator validator;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        validator = new CachingReferenceValidator(stringRedisTemplate, referenceManagerClient, 300);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
    }

    // ================================================================ AIRLINE

    @Nested
    @DisplayName("validateAirline")
    class ValidateAirline {

        @Test
        @DisplayName("cache hit: client çağrılmaz")
        void validateAirline_cacheHit_doesNotCallClient() {
            // given
            when(valueOperations.get("reference:AIRLINE:TK")).thenReturn(POSITIVE);

            // when
            boolean result = validator.airlineExists("TK");

            // then
            assertThat(result).isTrue();
            verify(referenceManagerClient, never()).getAirline(anyString());
        }

        @Test
        @DisplayName("cache miss: client çağrılır ve cache'lenir")
        void validateAirline_cacheMiss_callsClientAndCaches() {
            // given
            when(valueOperations.get("reference:AIRLINE:TK")).thenReturn(null);
            when(referenceManagerClient.getAirline("TK"))
                    .thenReturn(Optional.of(new AirlineDto(1L, "Turkish Airlines", "TK")));

            // when
            boolean result = validator.airlineExists("TK");

            // then
            assertThat(result).isTrue();
            verify(referenceManagerClient).getAirline("TK");
            verify(valueOperations).set(eq("reference:AIRLINE:TK"), eq(POSITIVE), any());
        }

        @Test
        @DisplayName("cache exception: fail-open, client çağrılır, exception dışarı çıkmaz")
        void validateAirline_cacheThrows_failOpen_callsClient() {
            // given
            when(valueOperations.get(anyString())).thenThrow(new QueryTimeoutException("timeout"));
            when(referenceManagerClient.getAirline("TK"))
                    .thenReturn(Optional.of(new AirlineDto(1L, "Turkish Airlines", "TK")));

            // when — exception fırlatmamalı
            boolean result = validator.airlineExists("TK");

            // then
            assertThat(result).isTrue();
            verify(referenceManagerClient).getAirline("TK");
        }

        @Test
        @DisplayName("client geçersiz (empty) döndürünce false döner")
        void validateAirline_clientReturnsInvalid_returnsFalse() {
            // given
            when(valueOperations.get(anyString())).thenReturn(null);
            when(referenceManagerClient.getAirline("XX")).thenReturn(Optional.empty());

            // when
            boolean result = validator.airlineExists("XX");

            // then
            assertThat(result).isFalse();
        }
    }

    // ============================================================= AIRCRAFT

    @Nested
    @DisplayName("validateAircraft")
    class ValidateAircraft {

        @Test
        @DisplayName("cache hit: client çağrılmaz")
        void validateAircraft_cacheHit_doesNotCallClient() {
            // given
            when(valueOperations.get("reference:AIRCRAFT:TC-JFA")).thenReturn(POSITIVE);

            // when
            boolean result = validator.aircraftExists("TC-JFA");

            // then
            assertThat(result).isTrue();
            verify(referenceManagerClient, never()).getAircraft(anyString());
        }

        @Test
        @DisplayName("cache miss: client çağrılır ve cache'lenir")
        void validateAircraft_cacheMiss_callsClientAndCaches() {
            // given
            when(valueOperations.get("reference:AIRCRAFT:TC-JFA")).thenReturn(null);
            when(referenceManagerClient.getAircraft("TC-JFA"))
                    .thenReturn(Optional.of(new AircraftDto(1L, "B738", "TC-JFA")));

            // when
            boolean result = validator.aircraftExists("TC-JFA");

            // then
            assertThat(result).isTrue();
            verify(valueOperations).set(eq("reference:AIRCRAFT:TC-JFA"), eq(POSITIVE), any());
        }

        @Test
        @DisplayName("cache exception: fail-open, client çağrılır")
        void validateAircraft_cacheThrows_failOpen_callsClient() {
            // given
            when(valueOperations.get(anyString())).thenThrow(new QueryTimeoutException("timeout"));
            when(referenceManagerClient.getAircraft("TC-JFA"))
                    .thenReturn(Optional.of(new AircraftDto(1L, "B738", "TC-JFA")));

            // when
            boolean result = validator.aircraftExists("TC-JFA");

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("client geçersiz döndürünce false döner")
        void validateAircraft_clientReturnsInvalid_returnsFalse() {
            // given
            when(valueOperations.get(anyString())).thenReturn(null);
            when(referenceManagerClient.getAircraft("XX")).thenReturn(Optional.empty());

            // when
            boolean result = validator.aircraftExists("XX");

            // then
            assertThat(result).isFalse();
        }
    }

    // ============================================================= STATION

    @Nested
    @DisplayName("validateStation")
    class ValidateStation {

        @Test
        @DisplayName("cache hit: client çağrılmaz")
        void validateStation_cacheHit_doesNotCallClient() {
            // given
            when(valueOperations.get("reference:STATION:LTFM")).thenReturn(POSITIVE);

            // when
            boolean result = validator.stationExists("LTFM");

            // then
            assertThat(result).isTrue();
            verify(referenceManagerClient, never()).getStation(anyString());
        }

        @Test
        @DisplayName("cache miss: client çağrılır ve cache'lenir")
        void validateStation_cacheMiss_callsClientAndCaches() {
            // given
            when(valueOperations.get("reference:STATION:LTFM")).thenReturn(null);
            when(referenceManagerClient.getStation("LTFM"))
                    .thenReturn(Optional.of(new StationDto(1L, "LTFM", "İstanbul Havalimanı")));

            // when
            boolean result = validator.stationExists("LTFM");

            // then
            assertThat(result).isTrue();
            verify(valueOperations).set(eq("reference:STATION:LTFM"), eq(POSITIVE), any());
        }

        @Test
        @DisplayName("cache exception: fail-open, client çağrılır")
        void validateStation_cacheThrows_failOpen_callsClient() {
            // given
            when(valueOperations.get(anyString())).thenThrow(new QueryTimeoutException("timeout"));
            when(referenceManagerClient.getStation("LTFM"))
                    .thenReturn(Optional.of(new StationDto(1L, "LTFM", "İstanbul")));

            // when
            boolean result = validator.stationExists("LTFM");

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("client geçersiz döndürünce false döner")
        void validateStation_clientReturnsInvalid_returnsFalse() {
            // given
            when(valueOperations.get(anyString())).thenReturn(null);
            when(referenceManagerClient.getStation("XXXX")).thenReturn(Optional.empty());

            // when
            boolean result = validator.stationExists("XXXX");

            // then
            assertThat(result).isFalse();
        }
    }

    // =============================================================== ROUTE

    @Nested
    @DisplayName("validateRoute")
    class ValidateRoute {

        @Test
        @DisplayName("cache hit: client çağrılmaz")
        void validateRoute_cacheHit_doesNotCallClient() {
            // given
            when(valueOperations.get("reference:ROUTE:LTFM-LTAC")).thenReturn(POSITIVE);

            // when
            boolean result = validator.routeExists("LTFM", "LTAC");

            // then
            assertThat(result).isTrue();
            verify(referenceManagerClient, never()).getRoute(anyString(), anyString());
        }

        @Test
        @DisplayName("cache miss: client çağrılır ve cache'lenir")
        void validateRoute_cacheMiss_callsClientAndCaches() {
            // given
            StationDto origin = new StationDto(1L, "LTFM", "İstanbul");
            StationDto dest   = new StationDto(2L, "LTAC", "Ankara");
            when(valueOperations.get("reference:ROUTE:LTFM-LTAC")).thenReturn(null);
            when(referenceManagerClient.getRoute("LTFM", "LTAC"))
                    .thenReturn(Optional.of(new RouteDto(10L, origin, dest)));

            // when
            boolean result = validator.routeExists("LTFM", "LTAC");

            // then
            assertThat(result).isTrue();
            verify(valueOperations).set(eq("reference:ROUTE:LTFM-LTAC"), eq(POSITIVE), any());
        }

        @Test
        @DisplayName("cache exception: fail-open, client çağrılır")
        void validateRoute_cacheThrows_failOpen_callsClient() {
            // given
            StationDto origin = new StationDto(1L, "LTFM", "İstanbul");
            StationDto dest   = new StationDto(2L, "LTAC", "Ankara");
            when(valueOperations.get(anyString())).thenThrow(new QueryTimeoutException("timeout"));
            when(referenceManagerClient.getRoute("LTFM", "LTAC"))
                    .thenReturn(Optional.of(new RouteDto(10L, origin, dest)));

            // when
            boolean result = validator.routeExists("LTFM", "LTAC");

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("client geçersiz döndürünce false döner")
        void validateRoute_clientReturnsInvalid_returnsFalse() {
            // given
            when(valueOperations.get(anyString())).thenReturn(null);
            when(referenceManagerClient.getRoute("LTFM", "XXXX")).thenReturn(Optional.empty());

            // when
            boolean result = validator.routeExists("LTFM", "XXXX");

            // then
            assertThat(result).isFalse();
        }
    }
}
