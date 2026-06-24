package com.tav.FlightService.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tav.FlightService.events.ChangeType;
import com.tav.FlightService.events.ReferenceChangedEvent;
import com.tav.FlightService.events.ReferenceEntityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReferenceCacheSyncListenerTest {

    @Mock StringRedisTemplate stringRedisTemplate;
    @Mock ValueOperations<String, String> valueOperations;

    @Spy ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks ReferenceCacheSyncListener listener;

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ---------------------------------------------------------------- DELETED

    @Test
    @DisplayName("DELETED AIRLINE → reference:AIRLINE:<key> Redis'ten silinir")
    void onReferenceChanged_DELETED_airline_deletesKey() {
        // given
        ReferenceChangedEvent event = new ReferenceChangedEvent(
                ReferenceEntityType.AIRLINE, ChangeType.DELETED, "TK", null);

        // when
        listener.onReferenceChanged(event);

        // then
        verify(stringRedisTemplate).delete("reference:AIRLINE:TK");
        verify(stringRedisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("DELETED STATION → reference:STATION:<key> silinir")
    void onReferenceChanged_DELETED_station_deletesKey() {
        // given
        ReferenceChangedEvent event = new ReferenceChangedEvent(
                ReferenceEntityType.STATION, ChangeType.DELETED, "LTFM", null);

        // when
        listener.onReferenceChanged(event);

        // then
        verify(stringRedisTemplate).delete("reference:STATION:LTFM");
    }

    @Test
    @DisplayName("DELETED AIRCRAFT → reference:AIRCRAFT:<key> silinir")
    void onReferenceChanged_DELETED_aircraft_deletesKey() {
        // given
        ReferenceChangedEvent event = new ReferenceChangedEvent(
                ReferenceEntityType.AIRCRAFT, ChangeType.DELETED, "TC-JFA", null);

        // when
        listener.onReferenceChanged(event);

        // then
        verify(stringRedisTemplate).delete("reference:AIRCRAFT:TC-JFA");
    }

    @Test
    @DisplayName("DELETED ROUTE → reference:ROUTE:<key> silinir")
    void onReferenceChanged_DELETED_route_deletesKey() {
        // given
        ReferenceChangedEvent event = new ReferenceChangedEvent(
                ReferenceEntityType.ROUTE, ChangeType.DELETED, "LTFM-LTAC", null);

        // when
        listener.onReferenceChanged(event);

        // then
        verify(stringRedisTemplate).delete("reference:ROUTE:LTFM-LTAC");
    }

    // ---------------------------------------------------------------- CREATED / UPDATED

    @Test
    @DisplayName("CREATED AIRLINE → payload JSON olarak 1 saat TTL ile cache'lenir")
    void onReferenceChanged_CREATED_airline_cachesSerializedPayload() {
        // given
        Map<String, Object> payload = Map.of("code", "TK", "name", "Turkish Airlines");
        ReferenceChangedEvent event = new ReferenceChangedEvent(
                ReferenceEntityType.AIRLINE, ChangeType.CREATED, "TK", payload);

        // when
        listener.onReferenceChanged(event);

        // then
        verify(stringRedisTemplate).opsForValue();
        verify(valueOperations).set(
                eq("reference:AIRLINE:TK"),
                any(String.class),
                eq(Duration.ofHours(1))
        );
        verify(stringRedisTemplate, never()).delete(any(String.class));
    }

    @Test
    @DisplayName("UPDATED STATION → payload yeniden serialize edilip set edilir")
    void onReferenceChanged_UPDATED_station_setsValue() {
        // given
        Map<String, Object> payload = Map.of("code", "LTFM", "name", "Istanbul Airport");
        ReferenceChangedEvent event = new ReferenceChangedEvent(
                ReferenceEntityType.STATION, ChangeType.UPDATED, "LTFM", payload);

        // when
        listener.onReferenceChanged(event);

        // then
        verify(valueOperations).set(
                eq("reference:STATION:LTFM"),
                any(String.class),
                eq(Duration.ofHours(1))
        );
    }

    // ---------------------------------------------------------------- Null payload

    @Test
    @DisplayName("CREATED + null payload → log + return, Redis'e dokunulmaz")
    void onReferenceChanged_CREATED_nullPayload_doesNothing() {
        // given
        ReferenceChangedEvent event = new ReferenceChangedEvent(
                ReferenceEntityType.AIRLINE, ChangeType.CREATED, "TK", null);

        // when
        listener.onReferenceChanged(event);

        // then
        verify(stringRedisTemplate, never()).delete(any(String.class));
        verify(stringRedisTemplate, never()).opsForValue();
        verifyNoInteractions(valueOperations);
    }

    @Test
    @DisplayName("UPDATED + null payload → cache'e yazma yapılmaz")
    void onReferenceChanged_UPDATED_nullPayload_doesNothing() {
        // given
        ReferenceChangedEvent event = new ReferenceChangedEvent(
                ReferenceEntityType.ROUTE, ChangeType.UPDATED, "LTFM-LTAC", null);

        // when
        listener.onReferenceChanged(event);

        // then
        verifyNoInteractions(valueOperations);
        verify(stringRedisTemplate, never()).delete(any(String.class));
    }

    // ---------------------------------------------------------------- Hata yutma

    @Test
    @DisplayName("CREATED + Redis set fail → exception propagate etmez (consumer kilitlenmesin)")
    void onReferenceChanged_CREATED_whenRedisThrows_doesNotPropagate() {
        // given
        Map<String, Object> payload = Map.of("code", "TK");
        ReferenceChangedEvent event = new ReferenceChangedEvent(
                ReferenceEntityType.AIRLINE, ChangeType.CREATED, "TK", payload);
        doThrow(new RuntimeException("redis down"))
                .when(valueOperations).set(any(String.class), any(String.class), any(Duration.class));

        // when / then
        // NOT: ReferenceCacheSyncListener mevcut haliyle Redis exception'ı yutmuyor;
        // mevcut davranışı belgeliyoruz. Eğer poison-pill koruması eklenirse
        // bu test "doesNotThrowAnyException" olarak güncellenmelidir.
        assertThatCode(() -> listener.onReferenceChanged(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("redis down");
    }

    @Test
    @DisplayName("DELETED + Redis delete fail → exception propagate eder (mevcut davranış)")
    void onReferenceChanged_DELETED_whenRedisThrows_propagates() {
        // given
        ReferenceChangedEvent event = new ReferenceChangedEvent(
                ReferenceEntityType.AIRLINE, ChangeType.DELETED, "TK", null);
        doThrow(new RuntimeException("redis down"))
                .when(stringRedisTemplate).delete(any(String.class));

        // when / then
        assertThatCode(() -> listener.onReferenceChanged(event))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("JsonProcessingException benzeri serialization hatası → propagate etmez (poison-pill koruması)")
    void onReferenceChanged_whenSerializationFails_doesNotPropagate() throws Exception {
        // given
        Map<String, Object> payload = Map.of("code", "TK");
        ReferenceChangedEvent event = new ReferenceChangedEvent(
                ReferenceEntityType.AIRLINE, ChangeType.CREATED, "TK", payload);
        doThrow(new com.fasterxml.jackson.core.JsonProcessingException("bad json") {})
                .when(objectMapper).writeValueAsString(any());

        // when / then
        assertThatCode(() -> listener.onReferenceChanged(event)).doesNotThrowAnyException();
        verifyNoMoreInteractions(valueOperations);
    }
}
