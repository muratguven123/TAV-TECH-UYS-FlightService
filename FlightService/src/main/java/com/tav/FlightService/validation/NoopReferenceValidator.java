package com.tav.FlightService.validation;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Test / geliştirme ortamı için Noop referans doğrulayıcı.
 * Etkinleştirmek: app.reference.validator=noop
 * Varsayılan: caching (CachingReferenceValidator @Primary'dir)
 */
@Component
@ConditionalOnProperty(name = "app.reference.validator", havingValue = "noop")
@Slf4j
public class NoopReferenceValidator implements ReferenceValidator {

    @PostConstruct
    public void warnIfActive() {
        log.warn("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        log.warn("NoopReferenceValidator AKTİF — referans validasyonu DEVRE DIŞI.");
        log.warn("Bu sadece test ortamında kullanılmalıdır!");
        log.warn("Production'da app.reference.validator=noop AYARLAMAYIN.");
        log.warn("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
    }

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
