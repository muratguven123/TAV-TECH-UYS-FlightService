package com.tav.FlightService.events;

import com.tav.FlightService.dto.FlightResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class FlightEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public FlightEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public void publish(FlightChangeType changeType, FlightResponse payload, String username) {
        FlightChangedEvent event = new FlightChangedEvent(
                changeType,
                payload.id(),
                payload.version(),
                username,
                Instant.now(),
                payload
        );
        applicationEventPublisher.publishEvent(event);
    }
}
