package com.tav.FlightService.events;

import com.tav.FlightService.dto.FlightResponse;
import com.tav.uys.events.FlightChangeType;
import com.tav.uys.events.FlightChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class FlightEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final FlightEventSequenceGenerator sequenceGenerator;

    public FlightEventPublisher(ApplicationEventPublisher applicationEventPublisher,
                                FlightEventSequenceGenerator sequenceGenerator) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.sequenceGenerator = sequenceGenerator;
    }

    public void publish(FlightChangeType changeType, FlightResponse payload, String username) {
        FlightChangedEvent event = new FlightChangedEvent(
                changeType,
                payload.id(),
                payload.version(),
                username,
                Instant.now(),
                payload,
                sequenceGenerator.next()
        );
        applicationEventPublisher.publishEvent(event);
    }
}
