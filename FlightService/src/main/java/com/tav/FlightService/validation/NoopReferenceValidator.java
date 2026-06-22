package com.tav.FlightService.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Geçici Noop implementasyon — Aşama 5'e kadar tüm referans doğrulama çağrıları
 * bu sınıf tarafından karşılanır ve her zaman true döner.
 *
 * Gerçek implementasyon hazır olduğunda bu sınıftan @Primary kaldırılır,
 * Redis/REST tabanlı gerçek sınıfa eklenir.
 */
@Primary
@Component
public class NoopReferenceValidator implements ReferenceValidator {

    private static final Logger log = LoggerFactory.getLogger(NoopReferenceValidator.class);

    @Override
    public boolean airlineExists(String code) {
        log.warn("[NOOP] airlineExists kontrolü atlandı: code={}", code);
        return true;
    }

    @Override
    public boolean aircraftExists(String tailNumber) {
        log.warn("[NOOP] aircraftExists kontrolü atlandı: tailNumber={}", tailNumber);
        return true;
    }

    @Override
    public boolean stationExists(String icao) {
        log.warn("[NOOP] stationExists kontrolü atlandı: icao={}", icao);
        return true;
    }

    @Override
    public boolean routeExists(String originIcao, String destinationIcao) {
        log.warn("[NOOP] routeExists kontrolü atlandı: {}->{}", originIcao, destinationIcao);
        return true;
    }
}
