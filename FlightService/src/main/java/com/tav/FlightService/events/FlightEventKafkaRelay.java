
package com.tav.FlightService.events;

import com.tav.uys.events.FlightChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * DB commit sonrası (AFTER_COMMIT) flight.events Kafka topic'ine event relay eder.
 *
 * Partition key = flightId (String) — aynı uçuşun event'leri aynı partition'a
 * gider; FAS (FlightArchiveService) sıralı tüketim için buna dayanır.
 */
@Slf4j
@Component
public class FlightEventKafkaRelay {

    static final String TOPIC = "flight.events";

    private final KafkaTemplate<String, FlightChangedEvent> kafkaTemplate;

    public FlightEventKafkaRelay(
            @Qualifier("flightEventKafkaTemplate")
            KafkaTemplate<String, FlightChangedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFlightChanged(FlightChangedEvent event) {
        String partitionKey = event.flightId().toString();
        kafkaTemplate.send(TOPIC, partitionKey, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("flight.events Kafka send failed flightId={} type={}",
                                event.flightId(), event.changeType(), ex);
                    } else {
                        log.debug("flight.events sent flightId={} type={} partition={}",
                                event.flightId(), event.changeType(),
                                result.getRecordMetadata().partition());
                    }
                });
    }
}
