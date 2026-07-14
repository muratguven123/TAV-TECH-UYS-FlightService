package com.tav.FlightService.cache;

import com.tav.FlightService.config.RedisKeys;
import com.tav.uys.events.ChangeType;
import com.tav.uys.events.ReferenceChangedEvent;
import com.tav.uys.events.ReferenceEntityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReferenceCacheSyncListenerTest {

    @Mock StringRedisTemplate stringRedisTemplate;
    @Mock ValueOperations<String, String> valueOperations;
    @Mock SimpMessagingTemplate messagingTemplate;

    ReferenceCacheSyncListener listener;

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        listener = new ReferenceCacheSyncListener(stringRedisTemplate, messagingTemplate);
    }

    // ---------------------------------------------------------------- DELETED

    @Test
    @DisplayName("DELETED AIRLINE → RedisKeys.fromEvent() ile üretilen anahtar Redis'ten silinir")
    void onReferenceChanged_DELETED_airline_deletesKey() {
        // FIX: TD-011 — hardcoded string yerine RedisKeys.fromEvent() ile beklenen anahtar
        ReferenceChangedEvent event = new ReferenceChangedEvent(
                ReferenceEntityType.AIRLINE, ChangeType.DELETED, "TK", null);

        listener.onReferenceChanged(event);

        verify(stringRedisTemplate).delete(RedisKeys.fromEvent("AIRLINE", "TK"));
        verify(stringRedisTemplate, never()).opsForValue();
        verifyWebSocketPush("AIRLINE", "DELETED", "TK");
    }

    @Test
    @DisplayName("DELETED STATION → RedisKeys.fromEvent() ile üretilen anahtar silinir")
    void onReferenceChanged_DELETED_station_deletesKey() {
        ReferenceChangedEvent event = new ReferenceChangedEvent(
                ReferenceEntityType.STATION, ChangeType.DELETED, "LTFM", null);

        listener.onReferenceChanged(event);

        verify(stringRedisTemplate).delete(RedisKeys.fromEvent("STATION", "LTFM"));
    }

    @Test
    @DisplayName("DELETED AIRCRAFT → RedisKeys.fromEvent() ile üretilen anahtar silinir")
    void onReferenceChanged_DELETED_aircraft_deletesKey() {
        ReferenceChangedEvent event = new ReferenceChangedEvent(
                ReferenceEntityType.AIRCRAFT, ChangeType.DELETED, "TC-JFA", null);

        listener.onReferenceChanged(event);

        verify(stringRedisTemplate).delete(RedisKeys.fromEvent("AIRCRAFT", "TC-JFA"));
    }

    @Test
    @DisplayName("DELETED ROUTE → RedisKeys.fromEvent() anahtarı silinir; route() ile aynı format (cache invalidation)")
    void onReferenceChanged_DELETED_route_deletesKey() {
        // FIX: TD-011 — kritik cross-service test: RouteService "LTFM-LTAC" formatında businessKey üretiyor
        ReferenceChangedEvent event = new ReferenceChangedEvent(
                ReferenceEntityType.ROUTE, ChangeType.DELETED, "LTFM-LTAC", null);

        listener.onReferenceChanged(event);

        // fromEvent("ROUTE","LTFM-LTAC") == RedisKeys.route("LTFM","LTAC") → invalidation çalışır
        verify(stringRedisTemplate).delete(RedisKeys.route("LTFM", "LTAC"));
    }

    // ---------------------------------------------------------------- CREATED / UPDATED

    @Test
    @DisplayName("CREATED AIRLINE → RedisKeys.fromEvent() anahtarına '1' yazılır (negatif cache invalidation)")
    void onReferenceChanged_CREATED_airline_writesPositive() {
        // FIX: TD-011 — hardcoded string yerine RedisKeys helper
        Map<String, Object> payload = Map.of("code", "TK", "name", "Turkish Airlines");
        ReferenceChangedEvent event = new ReferenceChangedEvent(
                ReferenceEntityType.AIRLINE, ChangeType.CREATED, "TK", payload);

        listener.onReferenceChanged(event);

        verify(valueOperations).set(RedisKeys.airline("TK"), "1", Duration.ofHours(1));
        verify(stringRedisTemplate, never()).delete(any(String.class));
        verifyWebSocketPush("AIRLINE", "CREATED", "TK");
    }

    @Test
    @DisplayName("UPDATED STATION → RedisKeys.fromEvent() anahtarına '1' yazılır")
    void onReferenceChanged_UPDATED_station_writesPositive() {
        Map<String, Object> payload = Map.of("code", "LTFM", "name", "Istanbul Airport");
        ReferenceChangedEvent event = new ReferenceChangedEvent(
                ReferenceEntityType.STATION, ChangeType.UPDATED, "LTFM", payload);

        listener.onReferenceChanged(event);

        verify(valueOperations).set(RedisKeys.station("LTFM"), "1", Duration.ofHours(1));
    }

    @Test
    @DisplayName("CREATED + null payload → yine '1' yazılır (referans oluşturulmuş)")
    void onReferenceChanged_CREATED_nullPayload_stillWritesPositive() {
        ReferenceChangedEvent event = new ReferenceChangedEvent(
                ReferenceEntityType.AIRLINE, ChangeType.CREATED, "TK", null);

        listener.onReferenceChanged(event);

        verify(valueOperations).set(RedisKeys.airline("TK"), "1", Duration.ofHours(1));
    }

    // ---------------------------------------------------------------- Redis hata yutma

    @Test
    @DisplayName("CREATED + Redis set fail → exception yutulur (consumer kilitlenmez)")
    void onReferenceChanged_CREATED_whenRedisThrows_doesNotPropagate() {
        Map<String, Object> payload = Map.of("code", "TK");
        ReferenceChangedEvent event = new ReferenceChangedEvent(
                ReferenceEntityType.AIRLINE, ChangeType.CREATED, "TK", payload);
        doThrow(new DataAccessException("redis down") {})
                .when(valueOperations).set(any(String.class), any(String.class), any(Duration.class));

        assertThatCode(() -> listener.onReferenceChanged(event)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("DELETED + Redis delete fail → exception yutulur")
    void onReferenceChanged_DELETED_whenRedisThrows_doesNotPropagate() {
        ReferenceChangedEvent event = new ReferenceChangedEvent(
                ReferenceEntityType.AIRLINE, ChangeType.DELETED, "TK", null);
        doThrow(new DataAccessException("redis down") {})
                .when(stringRedisTemplate).delete(any(String.class));

        assertThatCode(() -> listener.onReferenceChanged(event)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("CREATED + WebSocket push fail → exception yutulur")
    void onReferenceChanged_CREATED_whenWebSocketThrows_doesNotPropagate() {
        ReferenceChangedEvent event = new ReferenceChangedEvent(
                ReferenceEntityType.AIRLINE, ChangeType.CREATED, "TK", null);
        doThrow(new RuntimeException("ws down"))
                .when(messagingTemplate).convertAndSend(any(String.class), any(Object.class));

        assertThatCode(() -> listener.onReferenceChanged(event)).doesNotThrowAnyException();
    }

    private void verifyWebSocketPush(String entityType, String changeType, String businessKey) {
        verify(messagingTemplate).convertAndSend(
                eq("/topic/reference.changed"),
                eq(Map.of("entityType", entityType, "changeType", changeType, "businessKey", businessKey)));
    }
}
