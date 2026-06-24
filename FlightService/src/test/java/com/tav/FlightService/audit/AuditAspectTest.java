package com.tav.FlightService.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tav.FlightService.events.AuditCategory;
import com.tav.FlightService.events.AuditEvent;
import org.aspectj.lang.JoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditAspectTest {

    @Mock KafkaTemplate<String, AuditEvent> kafkaTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AuditAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new AuditAspect(kafkaTemplate, objectMapper);
        authenticate("alice");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------ SUCCESS

    @Test
    @DisplayName("afterSuccess → SUCCESS sonuçlu AuditEvent flight.audit topic'ine action key ile yollanır")
    void afterSuccess_emitsSuccessEventAndPublishesToKafka() {
        // given
        Auditable auditable = stubAuditable("CREATE_FLIGHT", AuditCategory.FLIGHT);
        Map<String, Object> result = Map.of("id", 42, "flightNumber", "TK0001");
        when(kafkaTemplate.send(any(String.class), any(String.class), any(AuditEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(mockSendResult()));

        // when
        aspect.afterSuccess(mock(JoinPoint.class), auditable, result);

        // then
        ArgumentCaptor<String> topicCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<AuditEvent> eventCap = ArgumentCaptor.forClass(AuditEvent.class);
        verify(kafkaTemplate).send(topicCap.capture(), keyCap.capture(), eventCap.capture());

        assertThat(topicCap.getValue()).isEqualTo("flight.audit");
        assertThat(keyCap.getValue()).isEqualTo("CREATE_FLIGHT");

        AuditEvent event = eventCap.getValue();
        assertThat(event.action()).isEqualTo("CREATE_FLIGHT");
        assertThat(event.category()).isEqualTo(AuditCategory.FLIGHT);
        assertThat(event.username()).isEqualTo("alice");
        assertThat(event.result()).isEqualTo("SUCCESS");
        assertThat(event.occurredAt()).isNotNull();
        assertThat(event.payload()).isNotNull();
        assertThat(event.payload().get("id").asInt()).isEqualTo(42);
        assertThat(event.payload().get("flightNumber").asText()).isEqualTo("TK0001");
    }

    @Test
    @DisplayName("afterSuccess → null result için payload JsonNode null tipinde olur, exception fırlatmaz")
    void afterSuccess_nullResult_publishesWithNullJsonNode() {
        // given
        Auditable auditable = stubAuditable("DELETE_FLIGHT", AuditCategory.FLIGHT);
        when(kafkaTemplate.send(any(String.class), any(String.class), any(AuditEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(mockSendResult()));

        // when
        assertThatCode(() -> aspect.afterSuccess(mock(JoinPoint.class), auditable, null))
                .doesNotThrowAnyException();

        // then
        ArgumentCaptor<AuditEvent> cap = ArgumentCaptor.forClass(AuditEvent.class);
        verify(kafkaTemplate).send(eq("flight.audit"), eq("DELETE_FLIGHT"), cap.capture());
        assertThat(cap.getValue().result()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("afterSuccess → category REFERENCE ise event.category REFERENCE olarak yayımlanır")
    void afterSuccess_referenceCategory_propagatedToEvent() {
        // given
        Auditable auditable = stubAuditable("REFRESH_CACHE", AuditCategory.REFERENCE);
        when(kafkaTemplate.send(any(String.class), any(String.class), any(AuditEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(mockSendResult()));

        // when
        aspect.afterSuccess(mock(JoinPoint.class), auditable, "ok");

        // then
        ArgumentCaptor<AuditEvent> cap = ArgumentCaptor.forClass(AuditEvent.class);
        verify(kafkaTemplate).send(any(String.class), any(String.class), cap.capture());
        assertThat(cap.getValue().category()).isEqualTo(AuditCategory.REFERENCE);
    }

    // ------------------------------------------------------------------ FAILURE

    @Test
    @DisplayName("afterFailure → FAILURE:<ExceptionClassName> formatlı result ve null payload ile yayımlanır")
    void afterFailure_emitsFailureEventWithExceptionClassName() {
        // given
        Auditable auditable = stubAuditable("UPDATE_FLIGHT", AuditCategory.FLIGHT);
        when(kafkaTemplate.send(any(String.class), any(String.class), any(AuditEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(mockSendResult()));
        RuntimeException ex = new IllegalArgumentException("bad input");

        // when
        aspect.afterFailure(mock(JoinPoint.class), auditable, ex);

        // then
        ArgumentCaptor<AuditEvent> cap = ArgumentCaptor.forClass(AuditEvent.class);
        verify(kafkaTemplate).send(eq("flight.audit"), eq("UPDATE_FLIGHT"), cap.capture());
        AuditEvent event = cap.getValue();
        assertThat(event.result()).isEqualTo("FAILURE:IllegalArgumentException");
        assertThat(event.payload()).isNull();
        assertThat(event.username()).isEqualTo("alice");
    }

    @Test
    @DisplayName("afterFailure → exception propagate edilmez (aspect yutar), Kafka send'e çağrı yapılır")
    void afterFailure_doesNotPropagateException() {
        // given
        Auditable auditable = stubAuditable("DELETE_FLIGHT", AuditCategory.FLIGHT);
        when(kafkaTemplate.send(any(String.class), any(String.class), any(AuditEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(mockSendResult()));

        // when / then
        assertThatCode(() -> aspect.afterFailure(
                mock(JoinPoint.class), auditable, new RuntimeException("boom")))
                .doesNotThrowAnyException();
        verify(kafkaTemplate).send(any(String.class), any(String.class), any(AuditEvent.class));
    }

    // ------------------------------------------------------------------ Kafka failure

    @Test
    @DisplayName("Kafka send fail olursa exception propagate etmez (whenComplete log'lar)")
    void sendAudit_whenKafkaFails_doesNotPropagate() {
        // given
        Auditable auditable = stubAuditable("CREATE_FLIGHT", AuditCategory.FLIGHT);
        CompletableFuture<SendResult<String, AuditEvent>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("kafka down"));
        when(kafkaTemplate.send(any(String.class), any(String.class), any(AuditEvent.class)))
                .thenReturn(failed);

        // when / then
        assertThatCode(() -> aspect.afterSuccess(mock(JoinPoint.class), auditable, "x"))
                .doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------ TOPIC

    @Test
    @DisplayName("TOPIC sabiti 'flight.audit' olmalıdır")
    void topicConstant_isFlightAudit() {
        assertThat(AuditAspect.TOPIC).isEqualTo("flight.audit");
    }

    // ------------------------------------------------------------------ helpers

    private Auditable stubAuditable(String action, AuditCategory category) {
        Auditable annotation = mock(Auditable.class);
        when(annotation.action()).thenReturn(action);
        when(annotation.category()).thenReturn(category);
        return annotation;
    }

    @SuppressWarnings("unchecked")
    private SendResult<String, AuditEvent> mockSendResult() {
        return (SendResult<String, AuditEvent>) mock(SendResult.class);
    }

    private void authenticate(String username) {
        var auth = new UsernamePasswordAuthenticationToken(username, "n/a", List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
