package com.tav.FlightService.dto;

import com.tav.FlightService.common.validation.ValidFlightNumber;
import com.tav.FlightService.common.validation.ValidIcao;
import com.tav.FlightService.domain.FlightType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CreateFlightRequest(

    @NotBlank
    @ValidFlightNumber
    String flightNumber,

    @NotBlank
    @Size(min = 2, max = 2)
    String airlineCode,

    @NotBlank
    String aircraftTailNumber,

    @NotBlank
    @ValidIcao
    String originStation,

    @NotBlank
    @ValidIcao
    String destinationStation,

    @NotNull
    FlightType flightType,

    @NotNull
    @Future
    LocalDateTime scheduledDeparture,

    @NotNull
    @Future
    LocalDateTime scheduledArrival
) {}
