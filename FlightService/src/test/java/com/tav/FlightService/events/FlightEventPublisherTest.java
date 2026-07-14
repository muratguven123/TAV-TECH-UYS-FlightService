package com.tav.FlightService.events;

import com.tav.uys.events.FlightChangedEvent;
import com.tav.uys.events.FlightChangeType;
import com.tav.FlightService.dto.FlightResponse;
import com.tav.FlightService.util.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlightEventPublisherTest {

    @Mock ApplicationEventPublisher applicationEventPublisher;
    @Mock FlightEventSequenceGenerator sequenceGenerator;

    @InjectMocks FlightEventPublisher publisher;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        when(sequenceGenerator.next()).thenReturn(1L, 2L, 3L, 4L);
    }

    @Test
    @DisplayName("publish(CREATED) → FlightChangedEvent doğru alanlarla yayımlanır")
    void publish_created_emitsFlightChangedEventWithCorrectPayload() {
        // given
        FlightResponse payload = TestDataFactory.buildFlightResponse();
        String username = "alice";
        Instant before = Instant.now();

        // when
        publisher.publish(FlightChangeType.CREATED, payload, username);

        // then
        ArgumentCaptor<FlightChangedEvent> captor = ArgumentCaptor.forClass(FlightChangedEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        FlightChangedEvent event = captor.getValue();

        assertThat(event.changeType()).isEqualTo(FlightChangeType.CREATED);
        assertThat(event.flightId()).isEqualTo(payload.id());
        assertThat(event.version()).isEqualTo(payload.version());
        assertThat(event.username()).isEqualTo(username);
        assertThat(event.payload()).isSameAs(payload);
        assertThat(event.occurredAt()).isBetween(before, Instant.now());
        assertThat(event.sequence()).isEqualTo(1L);
        verifyNoMoreInteractions(applicationEventPublisher);
    }

    @Test
    @DisplayName("publish(UPDATED) → changeType UPDATED olarak yayımlanır")
    void publish_updated_emitsUpdatedChangeType() {
        // given
        FlightResponse payload = TestDataFactory.buildFlightResponse();

        // when
        publisher.publish(FlightChangeType.UPDATED, payload, "bob");

        // then
        ArgumentCaptor<FlightChangedEvent> captor = ArgumentCaptor.forClass(FlightChangedEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().changeType()).isEqualTo(FlightChangeType.UPDATED);
        assertThat(captor.getValue().username()).isEqualTo("bob");
    }

    @Test
    @DisplayName("publish(DELETED) → changeType DELETED olarak yayımlanır")
    void publish_deleted_emitsDeletedChangeType() {
        // given
        FlightResponse payload = TestDataFactory.buildFlightResponse();

        // when
        publisher.publish(FlightChangeType.DELETED, payload, "carol");

        // then
        ArgumentCaptor<FlightChangedEvent> captor = ArgumentCaptor.forClass(FlightChangedEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().changeType()).isEqualTo(FlightChangeType.DELETED);
    }

    @Test
    @DisplayName("publish → username null olsa bile event publish edilir")
    void publish_nullUsername_stillPublishesEvent() {
        // given
        FlightResponse payload = TestDataFactory.buildFlightResponse();

        // when
        publisher.publish(FlightChangeType.CREATED, payload, null);

        // then
        ArgumentCaptor<FlightChangedEvent> captor = ArgumentCaptor.forClass(FlightChangedEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().username()).isNull();
    }
}
