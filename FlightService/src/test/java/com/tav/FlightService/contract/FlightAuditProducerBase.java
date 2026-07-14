package com.tav.FlightService.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tav.FlightService.events.AuditCategory;
import com.tav.FlightService.events.AuditEvent;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.verifier.messaging.boot.AutoConfigureMessageVerifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.Map;

/**
 * flight.audit producer kontratları için SCC base class.
 *
 * <p>Bu topic'te bilinen bir consumer servisi yoktur; kontratlar sadece
 * producer-side schema lock görevi görür: {@link AuditEvent} alanları
 * değişirse bu testler fail eder.
 *
 * <p>KafkaTemplate doğrudan inject edilir — {@link com.tav.FlightService.audit.AuditAspect}
 * JoinPoint bağlamı gerektirdiğinden bypass edilir.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:h2:mem:fs-audit-contract-db;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
        "spring.kafka.producer.properties.spring.json.add.type.headers=false"
})
@AutoConfigureMessageVerifier
@EmbeddedKafka(
        topics = {"flight.audit"},
        partitions = 1,
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@TestPropertySource(locations = "classpath:/contract-test.properties")
@org.springframework.context.annotation.Import(FlightAuditProducerBase.ContractVerifierBridgeConfig.class)
public abstract class FlightAuditProducerBase {

    @org.springframework.boot.test.mock.mockito.MockBean
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    static final String TOPIC = "flight.audit";

    @Autowired
    @Qualifier("auditKafkaTemplate")
    private KafkaTemplate<String, AuditEvent> auditKafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setup() {
        // no-op
    }

    /**
     * SCC tarafından triggerAuditSuccess() label'ıyla tetiklenir.
     */
    public void triggerAuditSuccess() {
        AuditEvent event = new AuditEvent(
                "CREATE_FLIGHT",
                AuditCategory.FLIGHT,
                "test-user",
                "SUCCESS",
                Instant.parse("2026-06-24T10:00:00Z"),
                objectMapper.valueToTree(Map.of("id", 1, "flightNumber", "TK0001"))
        );
        auditKafkaTemplate.send(TOPIC, event.action(), event);
    }

    /**
     * SCC tarafından triggerAuditFailure() label'ıyla tetiklenir.
     */
    public void triggerAuditFailure() {
        AuditEvent event = new AuditEvent(
                "CREATE_FLIGHT",
                AuditCategory.FLIGHT,
                "test-user",
                "FAILURE:BusinessException",
                Instant.parse("2026-06-24T10:00:00Z"),
                null
        );
        auditKafkaTemplate.send(TOPIC, event.action(), event);
    }

    @org.springframework.boot.test.context.TestConfiguration
    public static class ContractVerifierBridgeConfig {

        @org.springframework.context.annotation.Bean("flight.audit")
        public org.springframework.messaging.MessageChannel flightAuditChannel() {
            return new org.springframework.integration.channel.QueueChannel();
        }

        @org.springframework.kafka.annotation.KafkaListener(
                topics = "flight.audit",
                groupId = "contract-verifier-bridge-audit",
                properties = {
                    "spring.json.value.default.type=com.tav.FlightService.events.AuditEvent",
                    "spring.json.trusted.packages=com.tav.FlightService.events"
                }
        )
        public void bridgeToChannel(
                org.springframework.messaging.Message<com.tav.FlightService.events.AuditEvent> kafkaMessage
        ) {
            org.springframework.messaging.MessageChannel channel = flightAuditChannel();
            String key = (String) kafkaMessage.getHeaders().get(org.springframework.kafka.support.KafkaHeaders.RECEIVED_KEY);
            com.tav.FlightService.events.AuditEvent payload = kafkaMessage.getPayload();
            
            java.util.Map<String, Object> headers = new java.util.HashMap<>();
            headers.put("kafka_messageKey", key);
            
            org.springframework.messaging.Message<?> channelMessage = 
                    org.springframework.messaging.support.MessageBuilder
                            .withPayload(payload)
                            .copyHeaders(headers)
                            .build();
            channel.send(channelMessage);
        }
    }
}
