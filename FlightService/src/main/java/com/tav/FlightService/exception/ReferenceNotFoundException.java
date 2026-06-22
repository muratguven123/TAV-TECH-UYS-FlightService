package com.tav.FlightService.exception;

public class ReferenceNotFoundException extends RuntimeException {
    public ReferenceNotFoundException(String message) {
        super(message);
    }
}
