package com.tav.FlightService.validation;

/**
 * Referans varlık doğrulama SEAM'i.
 * Aşama 5'te Redis/REST tabanlı gerçek implementasyon bu interface'i implemente eder
 * ve @Primary ile NoopReferenceValidator'ın önüne geçer.
 */
public interface ReferenceValidator {

    boolean airlineExists(String code);

    boolean aircraftExists(String tailNumber);

    boolean stationExists(String icao);

    boolean routeExists(String originIcao, String destinationIcao);
}
