package com.tav.FlightService.cache;

import com.tav.FlightService.config.RedisKeys;
import com.tav.FlightService.config.StompTopics;
import com.tav.uys.events.ChangeType;
import com.tav.uys.events.ReferenceChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * Kafka reference.events topic'ini dinleyerek Redis referans cache'ini senkronize eder.
 *
 * <p>CachingReferenceValidator Redis'te "1" (pozitif) / "0" (negatif) değerleri kullanır.
 * Bu listener, CREATE/UPDATE event'lerinde key'i "1" ile günceller (negatif cache'i
 * geçersiz kılar), DELETE event'lerinde key'i siler.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReferenceCacheSyncListener {

    // FIX: TD-011 — hardcoded prefix RedisKeys.REFERENCE_PREFIX ile değiştirildi
    private static final String POSITIVE = "1";
    private static final Duration TTL = Duration.ofHours(1);

    private static final String REFERENCE_WS_TOPIC = StompTopics.REFERENCE_CHANGED;

    private final StringRedisTemplate stringRedisTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    @KafkaListener(topics = "reference.events", groupId = "reference-cache-sync")
    public void onReferenceChanged(ReferenceChangedEvent event) {
        // FIX: TD-011 — hardcoded string birleştirme RedisKeys.fromEvent() ile değiştirildi
        String redisKey = RedisKeys.fromEvent(event.entityType().name(), event.businessKey());

        try {
            if (event.changeType() == ChangeType.DELETED) {
                stringRedisTemplate.delete(redisKey);
                log.debug("Cache evicted (DELETE): key={}", redisKey);
            } else {
                // CREATE veya UPDATE → negatif cache'i geçersiz kıl, pozitif cache yaz
                stringRedisTemplate.opsForValue().set(redisKey, POSITIVE, TTL);
                log.debug("Cache updated to positive: key={}, changeType={}", redisKey, event.changeType());
            }
        } catch (DataAccessException e) {
            // Redis hatası consumer'ı bloklamamsalı — log ve devam
            log.error("Redis cache-sync hatası: key={}, changeType={}, hata={}",
                    redisKey, event.changeType(), e.getMessage(), e);
        }

        pushReferenceChangedNotification(event);
    }

    private void pushReferenceChangedNotification(ReferenceChangedEvent event) {
        try {
            messagingTemplate.convertAndSend(
                    REFERENCE_WS_TOPIC,
                    Map.of(
                            "entityType", event.entityType().name(),
                            "changeType", event.changeType().name(),
                            "businessKey", event.businessKey()));
            log.debug("WebSocket push → {} entityType={} businessKey={}",
                    REFERENCE_WS_TOPIC, event.entityType(), event.businessKey());
        } catch (Exception ex) {
            log.warn("WebSocket reference push failed entityType={} businessKey={}: {}",
                    event.entityType(), event.businessKey(), ex.getMessage());
        }
    }
}
