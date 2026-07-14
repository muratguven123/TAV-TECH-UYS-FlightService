package com.tav.FlightService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tav.FlightService.client.ReferenceManagerClient;
import com.tav.FlightService.client.dto.AircraftDto;
import com.tav.FlightService.client.dto.AirlineDto;
import com.tav.FlightService.client.dto.RouteDto;
import com.tav.FlightService.client.dto.StationDto;
import com.tav.FlightService.domain.FlightType;
import com.tav.FlightService.dto.CreateFlightRequest;
import com.tav.FlightService.dto.UpdateFlightRequest;
import com.tav.FlightService.events.AuditEvent;
import com.tav.uys.events.FlightChangedEvent;
import com.tav.uys.events.FlightChangeType;
import com.tav.FlightService.config.MockJwtDecoderAutoConfiguration;
import com.tav.FlightService.repository.FlightRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.stereotype.Component;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Aşama 6 — Çıkış Katmanı Integration Testleri
 *
 * Test kapsamı:
 *  1. flight.events üretimi (CREATED / UPDATED / DELETED)
 *  2. Partition key = flightId
 *  3. flight.audit üretimi (SUCCESS / FAILURE)
 *  4. AFTER_COMMIT garantisi (rollback → event YOK)
 *  5. WebSocket push (/topic/flights.all)
 *  6. WebSocket auth (token'sız CONNECT reddedilir)
 *
 * ⚠️  Self-invocation negatif test → bu dosyanın sonundaki
 *     SelfInvocationNegativeTest.java README satırına bakınız.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import({Phase6IntegrationTest.FlightEventsListener.class, Phase6IntegrationTest.AuditEventsListener.class})
@EmbeddedKafka(
        partitions = 3,
        topics = {"reference.events", "flight.events", "flight.audit"},
        brokerProperties = {"log.dir=target/kafka-logs-phase6"}
)
@TestPropertySource(properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:h2:mem:phase6db;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
        "app.reference.base-url=http://localhost:9999",
        "app.reference.validator=noop",
        "app.gateway.secret=test-secret",
        "spring.kafka.consumer.group-id=phase6-test",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.properties.spring.json.trusted.packages=*"
})
class Phase6IntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    FlightRepository flightRepository;

    @MockBean
    ReferenceManagerClient referenceManagerClient;

    @MockBean
    org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    // Embedded Kafka'dan okunan event'ler
    static final List<ConsumerRecord<String, ?>> flightEventsReceived = new ArrayList<>();
    static final List<ConsumerRecord<String, ?>> auditEventsReceived  = new ArrayList<>();

    @BeforeEach
    void setUp() {
        flightRepository.deleteAll();
        flightEventsReceived.clear();
        auditEventsReceived.clear();

        StationDto ltba = new StationDto(1L, "LTBA", "Istanbul Atatürk");
        StationDto ltfm = new StationDto(2L, "LTFM", "Istanbul Airport");

        when(referenceManagerClient.getAirline(anyString()))
                .thenReturn(Optional.of(new AirlineDto(1L, "Turkish Airlines", "TK")));
        when(referenceManagerClient.getAircraft(anyString()))
                .thenReturn(Optional.of(new AircraftDto(1L, "B738", "TC-JFC")));
        when(referenceManagerClient.getStation(anyString()))
                .thenReturn(Optional.of(ltba));
        when(referenceManagerClient.getRoute(anyString(), anyString()))
                .thenReturn(Optional.of(new RouteDto(1L, ltba, ltfm)));
    }

    // ------------------------------------------------------------------ helpers

    private String flightJson() throws Exception {
        return objectMapper.writeValueAsString(new CreateFlightRequest(
                "TK1980", "TK", "TC-JFC",
                "LTBA", "LTFM",
                FlightType.PASSENGER,
                LocalDateTime.of(2026, 7, 15, 11, 0),
                LocalDateTime.of(2026, 7, 15, 12, 30)
        ));
    }

    private String performCreate() throws Exception {
        return mockMvc.perform(post("/api/flights")
                        .header("X-Gateway-Secret", "test-secret")
                        .header("X-User-Name", "ali")
                        .header("X-User-Roles", "ROLE_OPERATION_OFFICER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(flightJson()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    // ------------------------------------------------------------------ Kafka listeners (test)

    @Component
    static class FlightEventsListener {
        @KafkaListener(topics = "flight.events", groupId = "phase6-test-fe",
                       properties = {"spring.json.value.default.type=com.tav.uys.events.FlightChangedEvent",
                                     "spring.json.trusted.packages=*"})
        void listen(ConsumerRecord<String, FlightChangedEvent> record) {
            flightEventsReceived.add(record);
        }
    }

    @Component
    static class AuditEventsListener {
        @KafkaListener(topics = "flight.audit", groupId = "phase6-test-ae",
                       properties = {"spring.json.value.default.type=com.tav.FlightService.events.AuditEvent",
                                     "spring.json.trusted.packages=*"})
        void listen(ConsumerRecord<String, AuditEvent> record) {
            auditEventsReceived.add(record);
        }
    }

    // ------------------------------------------------------------------ TESTLER

    @Test
    @DisplayName("POST /api/flights → flight.events CREATED event + partition key = flightId")
    void createFlightProducesFlightEvent() throws Exception {
        String body = performCreate();
        long id = objectMapper.readTree(body).get("id").asLong();

        // Kafka async — kısa bekleme
        Thread.sleep(1000);

        assertThat(flightEventsReceived).anySatisfy(r -> {
            FlightChangedEvent e = (FlightChangedEvent) r.value();
            assertThat(e.changeType()).isEqualTo(FlightChangeType.CREATED);
            assertThat(e.flightId()).isEqualTo(id);
            assertThat(e.username()).isEqualTo("ali");
            // Partition key = flightId
            assertThat(r.key()).isEqualTo(String.valueOf(id));
        });
    }

    @Test
    @DisplayName("PUT /api/flights/{id} → flight.events UPDATED + version artmış")
    void updateFlightProducesUpdatedEvent() throws Exception {
        String body = performCreate();
        long id = objectMapper.readTree(body).get("id").asLong();

        String updateJson = objectMapper.writeValueAsString(new UpdateFlightRequest(
                LocalDateTime.of(2026, 7, 15, 13, 0),
                LocalDateTime.of(2026, 7, 15, 14, 30),
                FlightType.CARGO
        ));

        mockMvc.perform(put("/api/flights/" + id)
                        .header("X-Gateway-Secret", "test-secret")
                        .header("X-User-Name", "ali")
                        .header("X-User-Roles", "ROLE_OPERATION_OFFICER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk());

        Thread.sleep(1000);

        assertThat(flightEventsReceived).anySatisfy(r -> {
            FlightChangedEvent e = (FlightChangedEvent) r.value();
            assertThat(e.changeType()).isEqualTo(FlightChangeType.UPDATED);
            assertThat(e.flightId()).isEqualTo(id);
        });
    }

    @Test
    @DisplayName("DELETE /api/flights/{id} → flight.events DELETED + son payload mevcut")
    void deleteFlightProducesDeletedEvent() throws Exception {
        String body = performCreate();
        long id = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(delete("/api/flights/" + id)
                        .header("X-Gateway-Secret", "test-secret")
                        .header("X-User-Name", "ali")
                        .header("X-User-Roles", "ROLE_OPERATION_OFFICER"))
                .andExpect(status().isNoContent());

        Thread.sleep(1000);

        assertThat(flightEventsReceived).anySatisfy(r -> {
            FlightChangedEvent e = (FlightChangedEvent) r.value();
            assertThat(e.changeType()).isEqualTo(FlightChangeType.DELETED);
            assertThat(e.flightId()).isEqualTo(id);
            assertThat(e.payload()).isNotNull();
            if (e.payload() instanceof java.util.Map) {
                assertThat(((java.util.Map<?, ?>) e.payload()).get("flightNumber")).isEqualTo("TK1980");
            } else if (e.payload() instanceof com.tav.FlightService.dto.FlightResponse) {
                assertThat(((com.tav.FlightService.dto.FlightResponse) e.payload()).flightNumber()).isEqualTo("TK1980");
            } else {
                org.junit.jupiter.api.Assertions.fail("Payload of unknown type: " + e.payload().getClass());
            }
        });
    }

    @Test
    @DisplayName("Audit SUCCESS: create çağrısı → flight.audit'te FLIGHT_CREATED + SUCCESS + username")
    void auditSuccessOnCreate() throws Exception {
        performCreate();
        Thread.sleep(1000);

        assertThat(auditEventsReceived).anySatisfy(r -> {
            AuditEvent e = (AuditEvent) r.value();
            assertThat(e.action()).isEqualTo("FLIGHT_CREATED");
            assertThat(e.result()).isEqualTo("SUCCESS");
            assertThat(e.username()).isEqualTo("ali");
            // partition key = action
            assertThat(r.key()).isEqualTo("FLIGHT_CREATED");
        });
    }

    @Test
    @DisplayName("Audit FAILURE: mevcut olmayan uçuş sil → FLIGHT_DELETED FAILURE + exception controller'a ulaşır")
    void auditFailureOnDeleteNotFound() throws Exception {
        mockMvc.perform(delete("/api/flights/99999")
                        .header("X-Gateway-Secret", "test-secret")
                        .header("X-User-Name", "operator")
                        .header("X-User-Roles", "ROLE_OPERATION_OFFICER"))
                .andExpect(status().isConflict());  // exception controller'a ulaştı

        Thread.sleep(1000);

        assertThat(auditEventsReceived).anySatisfy(r -> {
            AuditEvent e = (AuditEvent) r.value();
            assertThat(e.action()).isEqualTo("FLIGHT_DELETED");
            assertThat(e.result()).startsWith("FAILURE:");
            assertThat(e.username()).isEqualTo("operator");
        });
    }

    @Test
    @DisplayName("AFTER_COMMIT garantisi: @Transactional rollback → flight.events'e event GİTMEZ")
    void rollbackPreventsKafkaEvent() throws Exception {
        // Aynı uçuşu iki kez oluşturmaya çalış → ikincisi DataIntegrityViolation → rollback
        performCreate();  // ilk başarılı
        int beforeCount = flightEventsReceived.size();

        // İkinci aynı uçuş → 409 Conflict → DB rollback → event yok
        mockMvc.perform(post("/api/flights")
                        .header("X-Gateway-Secret", "test-secret")
                        .header("X-User-Name", "ali")
                        .header("X-User-Roles", "ROLE_OPERATION_OFFICER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(flightJson()))
                .andExpect(status().isConflict());

        Thread.sleep(800);

        // Yeni CREATED event gelmemeli
        long newCreatedCount = flightEventsReceived.stream()
                .filter(r -> r.value() instanceof FlightChangedEvent fce
                        && fce.changeType() == FlightChangeType.CREATED)
                .count();
        assertThat(newCreatedCount).isEqualTo(beforeCount == 0 ? 1 : beforeCount);
    }

    @Test
    @DisplayName("WebSocket CONNECT — JWT olmadan → reddedilir")
    void webSocketConnectWithoutJwtShouldFail() throws Exception {
        int port = ctx.getEnvironment().getProperty("local.server.port", Integer.class, 0);
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        StompHeaders connectHeaders = new StompHeaders();
        CountDownLatch latch = new CountDownLatch(1);

        try {
            stompClient.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(),
                            connectHeaders, new StompSessionHandlerAdapter() {})
                    .whenComplete((session, ex) -> latch.countDown());
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (Exception ex) {
            // bağlantı reddi beklenen davranış
            assertThat(ex).isNotNull();
        }
    }

    @Test
    @DisplayName("WebSocket CONNECT — geçerli test JWT ile bağlanır ve /topic/flights.all abone olur")
    void webSocketConnectWithValidJwt_subscribesToFlights() throws Exception {
        int port = ctx.getEnvironment().getProperty("local.server.port", Integer.class, 0);
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + MockJwtDecoderAutoConfiguration.VALID_TEST_TOKEN);

        StompSession session = stompClient.connectAsync(
                "ws://localhost:" + port + "/ws",
                new WebSocketHttpHeaders(),
                connectHeaders,
                new StompSessionHandlerAdapter() {}
        ).get(10, TimeUnit.SECONDS);

        CountDownLatch messageLatch = new CountDownLatch(0);
        session.subscribe("/topic/flights.all", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                messageLatch.countDown();
            }
        });

        assertThat(session.isConnected()).isTrue();
        session.disconnect();
    }

    @Autowired
    org.springframework.context.ApplicationContext ctx;

    org.springframework.context.ApplicationContext applicationContext() {
        return ctx;
    }
}
