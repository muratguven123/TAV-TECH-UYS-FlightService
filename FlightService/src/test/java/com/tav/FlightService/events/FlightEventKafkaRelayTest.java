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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlightEventKafkaRelayTest {

    @Mock KafkaTemplate<String, FlightChangedEvent> kafkaTemplate;

    @InjectMocks FlightEventKafkaRelay relay;

    @Test
    @DisplayName("onFlightChanged → flight.events topic'ine flightId key'iyle gönderir")
    void onFlightChanged_sendsToTopicWithFlightIdKey() {
        // given
        FlightResponse payload = TestDataFactory.buildFlightResponse();
        FlightChangedEvent event = new FlightChangedEvent(
                FlightChangeType.CREATED, payload.id(), payload.version(),
                "alice", Instant.now(), payload, 1L);

        SendResult<String, FlightChangedEvent> sendResult = mockSendResult();
        when(kafkaTemplate.send(eq("flight.events"), any(String.class), any(FlightChangedEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        // when
        relay.onFlightChanged(event);

        // then
        ArgumentCaptor<String> topicCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<FlightChangedEvent> valueCap = ArgumentCaptor.forClass(FlightChangedEvent.class);

        verify(kafkaTemplate).send(topicCap.capture(), keyCap.capture(), valueCap.capture());
        assertThat(topicCap.getValue()).isEqualTo("flight.events");
        assertThat(keyCap.getValue()).isEqualTo(payload.id().toString());
        assertThat(valueCap.getValue()).isSameAs(event);
        verifyNoMoreInteractions(kafkaTemplate);
    }

    @Test
    @DisplayName("onFlightChanged → UPDATED event aynı topic ve flightId key ile relay edilir")
    void onFlightChanged_updated_alsoUsesFlightIdKey() {
        // given
        FlightResponse payload = TestDataFactory.buildFlightResponse();
        FlightChangedEvent event = new FlightChangedEvent(
                FlightChangeType.UPDATED, payload.id(), 2L,
                "bob", Instant.now(), payload, 2L);

        when(kafkaTemplate.send(any(String.class), any(String.class), any(FlightChangedEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(mockSendResult()));

        // when
        relay.onFlightChanged(event);

        // then
        verify(kafkaTemplate).send(eq("flight.events"), eq(payload.id().toString()), eq(event));
    }

    @Test
    @DisplayName("onFlightChanged → DELETED event de relay edilir (tombstone değil, payload taşıyıcı)")
    void onFlightChanged_deleted_alsoRelayed() {
        // given
        FlightResponse payload = TestDataFactory.buildFlightResponse();
        FlightChangedEvent event = new FlightChangedEvent(
                FlightChangeType.DELETED, payload.id(), payload.version(),
                "carol", Instant.now(), payload, 3L);

        when(kafkaTemplate.send(any(String.class), any(String.class), any(FlightChangedEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(mockSendResult()));

        // when
        relay.onFlightChanged(event);

        // then
        verify(kafkaTemplate).send(eq("flight.events"), eq(payload.id().toString()), eq(event));
    }

    @Test
    @DisplayName("onFlightChanged → Kafka future fail olursa exception propagate etmez (log'lanır)")
    void onFlightChanged_whenKafkaFutureFails_doesNotPropagate() {
        // given
        FlightResponse payload = TestDataFactory.buildFlightResponse();
        FlightChangedEvent event = new FlightChangedEvent(
                FlightChangeType.CREATED, payload.id(), payload.version(),
                "alice", Instant.now(), payload, 1L);

        CompletableFuture<SendResult<String, FlightChangedEvent>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("kafka down"));
        when(kafkaTemplate.send(any(String.class), any(String.class), any(FlightChangedEvent.class)))
                .thenReturn(failed);

        // when / then
        assertThatCode(() -> relay.onFlightChanged(event)).doesNotThrowAnyException();
        verify(kafkaTemplate).send(eq("flight.events"), eq(payload.id().toString()), eq(event));
    }

    @Test
    @DisplayName("TOPIC sabiti 'flight.events' olmalı (FAS sözleşmesi)")
    void topicConstant_isFlightEvents() {
        assertThat(FlightEventKafkaRelay.TOPIC).isEqualTo("flight.events");
    }

    @SuppressWarnings("unchecked")
    private SendResult<String, FlightChangedEvent> mockSendResult() {
        return (SendResult<String, FlightChangedEvent>) mock(SendResult.class);
    }
}
