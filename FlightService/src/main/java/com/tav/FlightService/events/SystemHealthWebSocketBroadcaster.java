package com.tav.FlightService.events;

import com.tav.FlightService.config.StompTopics;
import com.tav.uys.events.SystemHealthChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * MonitoringService Kafka olaylarını STOMP /topic/system.health kanalına iletir.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemHealthWebSocketBroadcaster {

    static final String TOPIC = StompTopics.SYSTEM_HEALTH;

    private final SimpMessagingTemplate messagingTemplate;

    @KafkaListener(topics = "system.health.events", groupId = "system-health-ws")
    public void onHealthChanged(SystemHealthChangedEvent event) {
        try {
            messagingTemplate.convertAndSend(TOPIC, event);
            log.debug("WebSocket push → {} componentKey={} status={}",
                    TOPIC, event.componentKey(), event.component().status());
        } catch (Exception ex) {
            log.warn("WebSocket system health push failed componentKey={}: {}",
                    event.componentKey(), ex.getMessage());
        }
    }
}
