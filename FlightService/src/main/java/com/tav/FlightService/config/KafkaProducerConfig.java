package com.tav.FlightService.config;

import com.tav.FlightService.events.AuditEvent;
import com.tav.uys.events.FlightChangedEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Ayrı KafkaTemplate bean'leri:
 *
 *   flightEventKafkaTemplate  → flight.events topic'i için
 *   auditKafkaTemplate        → flight.audit  topic'i için
 *
 * İkisi de aynı ProducerFactory altyapısını paylaşır (idempotent, acks=all).
 * Farklı generic parametreler için ayrı bean tanımı Spring'in tip çözümleme
 * belirsizliğini (@Qualifier olmadan) ortadan kaldırır.
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Ortak producer ayarları — idempotent, en güçlü güvence.
     */
    private Map<String, Object> commonProducerProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        // Tip bilgisini header'a yazma — consumer kendi tip eşlemesini yönetir
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return props;
    }

    private <T> DefaultKafkaProducerFactory<String, T> createProducerFactory(
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new DefaultKafkaProducerFactory<>(
                commonProducerProps(),
                new StringSerializer(),
                new JsonSerializer<>(objectMapper)
        );
    }

    @Bean
    public ProducerFactory<String, Object> commonProducerFactory(
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return createProducerFactory(objectMapper);
    }

    /**
     * flight.events topic'i için KafkaTemplate.
     * Inject ederken: @Qualifier("flightEventKafkaTemplate")
     */
    @Bean("flightEventKafkaTemplate")
    public KafkaTemplate<String, FlightChangedEvent> flightEventKafkaTemplate(
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new KafkaTemplate<>(createProducerFactory(objectMapper));
    }

    /**
     * flight.audit topic'i için KafkaTemplate.
     * Inject ederken: @Qualifier("auditKafkaTemplate")
     */
    @Bean("auditKafkaTemplate")
    public KafkaTemplate<String, AuditEvent> auditKafkaTemplate(
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new KafkaTemplate<>(createProducerFactory(objectMapper));
    }
}
