package com.tav.FlightService.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tav.FlightService.events.ChangeType;
import com.tav.FlightService.events.ReferenceChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReferenceCacheSyncListener {

    private static final String KEY_PREFIX = "reference:";
    private static final Duration TTL = Duration.ofHours(1);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "reference.events", groupId = "reference-cache-sync")
    public void onReferenceChanged(ReferenceChangedEvent event) {
        String redisKey = KEY_PREFIX + event.entityType().name() + ":" + event.businessKey();

        if (event.changeType() == ChangeType.DELETED) {
            stringRedisTemplate.delete(redisKey);
            log.debug("Cache evicted: key={}", redisKey);
            return;
        }

        if (event.payload() == null) {
            log.warn("Received {} event with null payload for key={}, skipping cache update",
                    event.changeType(), redisKey);
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(event.payload());
            stringRedisTemplate.opsForValue().set(redisKey, json, TTL);
            log.debug("Cache updated: key={}, changeType={}", redisKey, event.changeType());
        } catch (JsonProcessingException e) {
            // Log and commit — do not rethrow; poison-pill messages must not block the consumer.
            log.error("Failed to serialize payload for cache key={}: {}", redisKey, e.getMessage(), e);
        }
    }
}
