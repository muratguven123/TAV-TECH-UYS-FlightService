package com.tav.FlightService.exception;

public class FlightConflictException extends RuntimeException {
    public FlightConflictException(String message) {
        super(message);
    }
}
