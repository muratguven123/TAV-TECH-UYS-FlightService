package com.tav.FlightService.events;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FlightEventSequenceGeneratorTest {

    @Test
    @DisplayName("next() monoton artan sequence üretir")
    void next_isMonotonicallyIncreasing() {
        FlightEventSequenceGenerator generator = new FlightEventSequenceGenerator();

        assertThat(generator.next()).isEqualTo(1L);
        assertThat(generator.next()).isEqualTo(2L);
        assertThat(generator.next()).isEqualTo(3L);
    }
}
