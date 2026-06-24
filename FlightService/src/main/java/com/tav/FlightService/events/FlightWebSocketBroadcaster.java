package com.tav.FlightService.events;

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
        var payload = event.payload();

        // 1) Tüm uçuşlar
        send("/topic/flights/all", event);

        // 2) Havayolu bazlı
        if (payload.airlineCode() != null) {
            send("/topic/flights/airline/" + payload.airlineCode(), event);
        }

        // 3) Kalkış istasyonu
        if (payload.originStation() != null) {
            send("/topic/flights/station/" + payload.originStation(), event);
        }

        // 4) Varış istasyonu
        if (payload.destinationStation() != null) {
            send("/topic/flights/station/" + payload.destinationStation(), event);
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
