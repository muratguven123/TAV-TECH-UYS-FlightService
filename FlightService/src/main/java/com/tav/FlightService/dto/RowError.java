package com.tav.FlightService.dto;

public record RowError(
    int rowNumber,
    String rawLine,
    String error
) {}
