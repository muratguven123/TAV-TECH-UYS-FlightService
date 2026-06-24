package com.tav.FlightService.exception;

/**
 * Kaynak bulunamadığında fırlatılır; HTTP 404 Not Found ile eşlenir.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
