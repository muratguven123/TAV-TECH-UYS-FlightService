package com.tav.FlightService.events;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * WebSocket/Kafka flight event'leri için monoton artan sequence üretir.
 * Gap detection ve reconnect sonrası snapshot senkronizasyonu için kullanılır.
 */
@Component
public class FlightEventSequenceGenerator {

    private final AtomicLong counter = new AtomicLong(0);

    public long next() {
        return counter.incrementAndGet();
    }
}
