package com.tav.FlightService.events;

import com.tav.FlightService.dto.FlightResponse;

import java.time.Instant;

public record FlightChangedEvent(
        FlightChangeType changeType,
        Long flightId,
        Long version,
        String username,
        Instant occurredAt,
        FlightResponse payload
) {}
