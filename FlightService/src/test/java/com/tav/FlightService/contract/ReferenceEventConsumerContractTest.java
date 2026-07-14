package com.tav.FlightService.contract;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.contract.verifier.messaging.boot.AutoConfigureMessageVerifier;
import org.springframework.cloud.contract.stubrunner.StubTrigger;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.messaging.MessageChannel;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tav.uys.events.ReferenceChangedEvent;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FlightService — {@code reference.events} consumer contract testi.
 *
 * <p>referance_manager stub jar'ından tetiklenen mesajlar, embedded Kafka üzerinden
 * {@link com.tav.FlightService.cache.ReferenceCacheSyncListener}'a ulaşır.
 * Test, Redis operasyonlarını doğrular:
 * <ul>
 *   <li>DELETED → {@code stringRedisTemplate.delete("reference:TYPE:key")}
 *   <li>CREATED/UPDATED → {@code valueOperations.set(...)} ile cache güncelleme
 * </ul>
 *
 * <p>Stub jar'ı çalıştırmadan önce {@code referance_manager} modülünde
 * {@code mvn install -DskipTests} çalıştırın.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:h2:mem:fs-ref-contract-db;MODE=MySQL;DB_CLOSE_DELAY=-1", // H2 specific connection
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "app.gateway.auth.enabled=false",
        "app.reference.validator=noop",
        // Triggered stub messages flow through the ContractVerifierConfig
        // "reference.events" DirectChannel bridge (below), which deserializes the
        // contract body to ReferenceChangedEvent and republishes it to the embedded
        // Kafka topic that the real ReferenceCacheSyncListener consumes. That bridge
        // is only exercised under the *integration* stub transport — not kafka.
        "stubrunner.stream.enabled=false",
        "stubrunner.integration.enabled=true",
        "stubrunner.kafka.enabled=false",
        "spring.kafka.consumer.group-id=fs-ref-contract-test",
        "spring.kafka.consumer.properties.spring.json.value.default.type=" +
                "com.tav.uys.events.ReferenceChangedEvent",
        "spring.kafka.consumer.properties.spring.json.trusted.packages=" +
                "com.tav.uys.events",
        "spring.kafka.consumer.properties.spring.json.use.type.headers=false"
})
@EmbeddedKafka(
        topics = {"reference.events"},
        partitions = 1,
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@AutoConfigureStubRunner(
        ids = "com.tav:referance_manager:+:stubs",
        stubsMode = StubRunnerProperties.StubsMode.LOCAL
)
@AutoConfigureMessageVerifier
@TestPropertySource(locations = "classpath:/contract-test.properties")
class ReferenceEventConsumerContractTest {

    @Autowired
    private StubTrigger stubTrigger;

    @TestConfiguration
    static class ContractVerifierConfig {
        @Bean("reference.events")
        public MessageChannel referenceEventsChannel(ProducerFactory<String, Object> producerFactory) {
            DirectChannel channel = new DirectChannel();
            final KafkaTemplate<String, Object> kafkaTemplate = new KafkaTemplate<>(producerFactory);
            final ObjectMapper objectMapper = new ObjectMapper();
            channel.subscribe(message -> {
                Object payload = message.getPayload();
                if (payload instanceof String json) {
                    try {
                        ReferenceChangedEvent event = objectMapper.readValue(json, ReferenceChangedEvent.class);
                        kafkaTemplate.send("reference.events", event);
                    } catch (Exception e) {
                        kafkaTemplate.send("reference.events", payload);
                    }
                } else if (payload instanceof byte[] bytes) {
                    try {
                        ReferenceChangedEvent event = objectMapper.readValue(bytes, ReferenceChangedEvent.class);
                        kafkaTemplate.send("reference.events", event);
                    } catch (Exception e) {
                        kafkaTemplate.send("reference.events", payload);
                    }
                } else {
                    kafkaTemplate.send("reference.events", payload);
                }
            });
            return channel;
        }
    }

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    private ValueOperations<String, String> valueOps;

    @BeforeEach
    void setupRedisMock() {
        valueOps = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ──────────────────────────────────────────────────────────
    // AIRLINE
    // ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("AIRLINE CREATED → Redis cache güncellenmeli")
    void airlineCreated_redisCacheGüncellenmeli() {
        stubTrigger.trigger("triggerAirlineCreated");

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        verify(valueOps, atLeastOnce())
                                .set(eq("reference:AIRLINE:TK"), anyString(), any()));
    }

    @Test
    @DisplayName("AIRLINE DELETED → Redis key silinmeli")
    void airlineDeleted_redisKeySilinmeli() {
        stubTrigger.trigger("triggerAirlineDeleted");

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        verify(stringRedisTemplate, atLeastOnce()).delete("reference:AIRLINE:TK"));
    }

    // ──────────────────────────────────────────────────────────
    // STATION
    // ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("STATION CREATED → Redis cache güncellenmeli")
    void stationCreated_redisCacheGüncellenmeli() {
        stubTrigger.trigger("triggerStationCreated");

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        verify(valueOps, atLeastOnce())
                                .set(eq("reference:STATION:LTFM"), anyString(), any()));
    }

    @Test
    @DisplayName("STATION UPDATED → Redis cache güncellenmeli")
    void stationUpdated_redisCacheGüncellenmeli() {
        stubTrigger.trigger("triggerStationUpdated");

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        verify(valueOps, atLeastOnce())
                                .set(eq("reference:STATION:LTFM"), anyString(), any()));
    }

    // ──────────────────────────────────────────────────────────
    // AIRCRAFT
    // ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("AIRCRAFT CREATED → Redis cache güncellenmeli")
    void aircraftCreated_redisCacheGüncellenmeli() {
        stubTrigger.trigger("triggerAircraftCreated");

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        verify(valueOps, atLeastOnce())
                                .set(eq("reference:AIRCRAFT:TC-JFA"), anyString(), any()));
    }

    // ──────────────────────────────────────────────────────────
    // ROUTE
    // ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("ROUTE CREATED → Redis cache güncellenmeli")
    void routeCreated_redisCacheGüncellenmeli() {
        stubTrigger.trigger("triggerRouteCreated");

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        verify(valueOps, atLeastOnce())
                                .set(eq("reference:ROUTE:LTFM-LTAC"), anyString(), any()));
    }

    @Test
    @DisplayName("ROUTE DELETED → Redis key silinmeli")
    void routeDeleted_redisKeySilinmeli() {
        stubTrigger.trigger("triggerRouteDeleted");

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        verify(stringRedisTemplate, atLeastOnce())
                                .delete("reference:ROUTE:LTFM-LTAC"));
    }
}
