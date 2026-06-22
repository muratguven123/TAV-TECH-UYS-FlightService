package com.tav.FlightService.dto;

import com.tav.FlightService.domain.FlightType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record UpdateFlightRequest(
        @NotNull @Future LocalDateTime scheduledDeparture,
        @NotNull @Future LocalDateTime scheduledArrival,
        @NotNull FlightType flightType
) {}
