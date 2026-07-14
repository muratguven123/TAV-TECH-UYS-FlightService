package com.tav.FlightService.events;

import com.tav.uys.events.FlightChangedEvent;
import com.tav.FlightService.config.StompTopics;
import com.tav.FlightService.dto.FlightResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class FlightWebSocketBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    public FlightWebSocketBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFlightChanged(FlightChangedEvent event) {
        if (!(event.payload() instanceof FlightResponse payload)) {
            log.warn("FlightChangedEvent payload is not a FlightResponse: {}",
                    event.payload() != null ? event.payload().getClass().getName() : "null");
            return;
        }

        // 1) Tüm uçuşlar
        send(StompTopics.FLIGHTS_ALL, event);

        // 2) Havayolu bazlı
        if (payload.airlineCode() != null) {
            send(StompTopics.FLIGHTS_AIRLINE_PREFIX + payload.airlineCode(), event);
        }

        // 3) Kalkış istasyonu
        if (payload.originStation() != null) {
            send(StompTopics.FLIGHTS_STATION_PREFIX + payload.originStation(), event);
        }

        // 4) Varış istasyonu
        if (payload.destinationStation() != null) {
            send(StompTopics.FLIGHTS_STATION_PREFIX + payload.destinationStation(), event);
        }
    }

    private void send(String destination, FlightChangedEvent event) {
        try {
            messagingTemplate.convertAndSend(destination, event);
            log.debug("WebSocket push → {} flightId={}", destination, event.flightId());
        } catch (Exception ex) {
            // Bağlantı kopuksa kaybolur — kabul edilmiş davranış
            log.warn("WebSocket push failed → {} flightId={}: {}", destination, event.flightId(), ex.getMessage());
        }
    }
}
