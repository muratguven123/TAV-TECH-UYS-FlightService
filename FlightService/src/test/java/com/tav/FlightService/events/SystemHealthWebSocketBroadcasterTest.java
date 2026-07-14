package com.tav.FlightService.events;

import com.tav.uys.events.SystemHealthChangedEvent;
import com.tav.uys.events.SystemHealthChangedEvent.HealthComponentSnapshot;
import com.tav.uys.events.SystemHealthChangedEvent.HealthMonitoringReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SystemHealthWebSocketBroadcasterTest {

    @Mock
    SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    SystemHealthWebSocketBroadcaster broadcaster;

    @Test
    @DisplayName("onHealthChanged → /topic/system.health kanalına iletir")
    void pushesToStompTopic() {
        HealthComponentSnapshot redis = new HealthComponentSnapshot(
                "redis", "REDIS", "DOWN", Instant.now(), 50L, "timeout", null);
        SystemHealthChangedEvent event = new SystemHealthChangedEvent(
                "redis",
                "UP",
                redis,
                new HealthMonitoringReport("DEGRADED", Instant.now(), List.of(), redis, redis));

        broadcaster.onHealthChanged(event);

        verify(messagingTemplate).convertAndSend(SystemHealthWebSocketBroadcaster.TOPIC, event);
    }
}
