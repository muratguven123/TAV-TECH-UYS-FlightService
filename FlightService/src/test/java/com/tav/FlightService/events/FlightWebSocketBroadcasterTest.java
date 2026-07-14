package com.tav.FlightService.events;

import com.tav.uys.events.FlightChangedEvent;
import com.tav.uys.events.FlightChangeType;
import com.tav.FlightService.dto.FlightResponse;
import com.tav.FlightService.domain.FlightType;
import com.tav.FlightService.util.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class FlightWebSocketBroadcasterTest {

    @Mock SimpMessagingTemplate messagingTemplate;

    @InjectMocks FlightWebSocketBroadcaster broadcaster;

    @Test
    @DisplayName("onFlightChanged → /topic/flights.all + airline + 2 station destination'ına gönderilir")
    void onFlightChanged_fullPayload_sendsToAllFourDestinations() {
        // given
        FlightResponse payload = TestDataFactory.buildFlightResponse();
        FlightChangedEvent event = new FlightChangedEvent(
                FlightChangeType.CREATED, payload.id(), payload.version(),
                "alice", Instant.now(), payload, 1L);

        // when
        broadcaster.onFlightChanged(event);

        // then
        verify(messagingTemplate).convertAndSend("/topic/flights.all", event);
        verify(messagingTemplate).convertAndSend("/topic/flights.airline." + payload.airlineCode(), event);
        verify(messagingTemplate).convertAndSend("/topic/flights.station." + payload.originStation(), event);
        verify(messagingTemplate).convertAndSend("/topic/flights.station." + payload.destinationStation(), event);
        verifyNoMoreInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("onFlightChanged → airlineCode null ise airline destination'ına gönderilmez")
    void onFlightChanged_nullAirline_skipsAirlineDestination() {
        // given
        FlightResponse payload = new FlightResponse(
                1L, "TK001", null, "TC-JFA",
                "LTFM", "LTAC", FlightType.PASSENGER,
                LocalDateTime.now(), LocalDateTime.now(), LocalDate.now(), 0L);
        FlightChangedEvent event = new FlightChangedEvent(
                FlightChangeType.UPDATED, 1L, 0L, "alice", Instant.now(), payload, 1L);

        // when
        broadcaster.onFlightChanged(event);

        // then
        verify(messagingTemplate).convertAndSend("/topic/flights.all", event);
        verify(messagingTemplate).convertAndSend("/topic/flights.station.LTFM", event);
        verify(messagingTemplate).convertAndSend("/topic/flights.station.LTAC", event);
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/flights.airline.null"), any(Object.class));
        verifyNoMoreInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("onFlightChanged → originStation null ise origin destination'ına gönderilmez")
    void onFlightChanged_nullOrigin_skipsOriginDestination() {
        // given
        FlightResponse payload = new FlightResponse(
                1L, "TK001", "TK", "TC-JFA",
                null, "LTAC", FlightType.PASSENGER,
                LocalDateTime.now(), LocalDateTime.now(), LocalDate.now(), 0L);
        FlightChangedEvent event = new FlightChangedEvent(
                FlightChangeType.UPDATED, 1L, 0L, "alice", Instant.now(), payload, 1L);

        // when
        broadcaster.onFlightChanged(event);

        // then
        verify(messagingTemplate).convertAndSend("/topic/flights.all", event);
        verify(messagingTemplate).convertAndSend("/topic/flights.airline.TK", event);
        verify(messagingTemplate).convertAndSend("/topic/flights.station.LTAC", event);
        verifyNoMoreInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("onFlightChanged → destinationStation null ise destination'a gönderilmez")
    void onFlightChanged_nullDestination_skipsDestinationStation() {
        // given
        FlightResponse payload = new FlightResponse(
                1L, "TK001", "TK", "TC-JFA",
                "LTFM", null, FlightType.PASSENGER,
                LocalDateTime.now(), LocalDateTime.now(), LocalDate.now(), 0L);
        FlightChangedEvent event = new FlightChangedEvent(
                FlightChangeType.UPDATED, 1L, 0L, "alice", Instant.now(), payload, 1L);

        // when
        broadcaster.onFlightChanged(event);

        // then
        verify(messagingTemplate).convertAndSend("/topic/flights.all", event);
        verify(messagingTemplate).convertAndSend("/topic/flights.airline.TK", event);
        verify(messagingTemplate).convertAndSend("/topic/flights.station.LTFM", event);
        verifyNoMoreInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("onFlightChanged → herhangi bir push fail ederse propagate etmez (log'lanır)")
    void onFlightChanged_whenTemplateThrows_doesNotPropagate() {
        // given
        FlightResponse payload = TestDataFactory.buildFlightResponse();
        FlightChangedEvent event = new FlightChangedEvent(
                FlightChangeType.CREATED, payload.id(), payload.version(),
                "alice", Instant.now(), payload, 1L);
        doThrow(new RuntimeException("ws disconnected"))
                .when(messagingTemplate).convertAndSend(any(String.class), any(Object.class));

        // when / then
        assertThatCode(() -> broadcaster.onFlightChanged(event)).doesNotThrowAnyException();
        // Bütün 4 destination denemesi yapılmalı — hata yutulduğu için sıradaki destination'lar atlanmaz
        verify(messagingTemplate).convertAndSend("/topic/flights.all", event);
        verify(messagingTemplate).convertAndSend("/topic/flights.airline." + payload.airlineCode(), event);
        verify(messagingTemplate).convertAndSend("/topic/flights.station." + payload.originStation(), event);
        verify(messagingTemplate).convertAndSend("/topic/flights.station." + payload.destinationStation(), event);
    }
}
