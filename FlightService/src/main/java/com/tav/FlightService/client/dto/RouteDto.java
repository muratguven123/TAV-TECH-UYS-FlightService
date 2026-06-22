package com.tav.FlightService.client.dto;

public record RouteDto(Long id, StationDto originStation, StationDto destinationStation) {}
