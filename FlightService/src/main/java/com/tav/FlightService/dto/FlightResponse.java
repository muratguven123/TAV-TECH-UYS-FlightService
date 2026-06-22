package com.tav.FlightService.dto;

import com.tav.FlightService.domain.FlightType;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record FlightResponse(
    Long id,
    String flightNumber,
    String airlineCode,
    String aircraftTailNumber,
    String originStation,
    String destinationStation,
    FlightType flightType,
    LocalDateTime scheduledDeparture,
    LocalDateTime scheduledArrival,
    LocalDate flightDate,
    Long version
) {}
