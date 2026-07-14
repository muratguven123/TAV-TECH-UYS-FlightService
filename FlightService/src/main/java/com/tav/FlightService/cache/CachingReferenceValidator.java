package com.tav.FlightService.cache;

import com.tav.FlightService.client.ReferenceManagerClient;
import com.tav.FlightService.config.RedisKeys;
import com.tav.FlightService.validation.ReferenceValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Cache-aside referans doğrulayıcı (UC-03 / UC-04).
 *
 * Akış:
 *   1. Redis'te anahtar var mı? "1" → true (hit), "0" → false (negatif hit), null → miss
 *   2. Miss: RM REST çağrısı
 *   3. RM 200 → Redis'e "1" yaz (TTL 1 saat) + true döner
 *   4. RM 404 → Redis'e "0" yaz (TTL 5 dakika) + false döner  (negatif cache)
 *   5. Redis hatası → fail-open: log + RM'e düş
 *   6. RM hatası → ServiceUnavailableException (503)
 *
 * Redis anahtar şeması:
 *   reference:AIRLINE:{IATA}
 *   reference:AIRCRAFT:{tailNumber}
 *   reference:STATION:{ICAO}
 *   reference:ROUTE:{origin}-{destination}
 */
@Primary
@Component
@ConditionalOnProperty(name = "app.reference.validator", havingValue = "caching", matchIfMissing = true)
@Slf4j
public class CachingReferenceValidator implements ReferenceValidator {

    public static final String POSITIVE = "1";
    public static final String NEGATIVE = "0";

    private static final Duration TTL = Duration.ofHours(1);

    private final StringRedisTemplate redisTemplate;
    private final ReferenceManagerClient referenceManagerClient;
    private final Duration negativeTtl;

    public CachingReferenceValidator(
            StringRedisTemplate redisTemplate,
            ReferenceManagerClient referenceManagerClient,
            @Value("${app.reference.validator.negative-cache-ttl:300}") int negativeCacheTtlSeconds) {
        this.redisTemplate = redisTemplate;
        this.referenceManagerClient = referenceManagerClient;
        this.negativeTtl = Duration.ofSeconds(negativeCacheTtlSeconds);
        log.info("CachingReferenceValidator initialized: negativeTtl={}s", negativeCacheTtlSeconds);
    }

    // FIX: TD-011 — hardcoded key string'leri RedisKeys sabitleriyle değiştirildi
    @Override
    public boolean airlineExists(String iataCode) {
        return lookup(RedisKeys.airline(iataCode), () -> referenceManagerClient.getAirline(iataCode));
    }

    @Override
    public boolean aircraftExists(String tailNumber) {
        return lookup(RedisKeys.aircraft(tailNumber), () -> referenceManagerClient.getAircraft(tailNumber));
    }

    @Override
    public boolean stationExists(String icao) {
        return lookup(RedisKeys.station(icao), () -> referenceManagerClient.getStation(icao));
    }

    @Override
    public boolean routeExists(String originIcao, String destinationIcao) {
        return lookup(RedisKeys.route(originIcao, destinationIcao),
                () -> referenceManagerClient.getRoute(originIcao, destinationIcao));
    }

    /**
     * Ortak cache-aside helper.
     *
     * @param redisKey       Redis anahtarı
     * @param sourceSupplier Redis miss durumunda çağrılacak RM client metodu
     */
    private boolean lookup(String redisKey, Supplier<Optional<?>> sourceSupplier) {
        // UC-03: Cache hit (pozitif veya negatif)
        try {
            String cached = redisTemplate.opsForValue().get(redisKey);
            if (cached != null) {
                log.debug("Cache hit: {} → {}", redisKey, cached);
                return !NEGATIVE.equals(cached);
            }
        } catch (DataAccessException e) {
            // UC-05: Redis çökerse fail-open — RM'e düş
            log.warn("Redis okuma hatası (fail-open), RM'e düşülüyor: key={} hata={}", redisKey, e.getMessage());
        }

        // UC-04: Cache miss → RM
        // ServiceUnavailableException fırlatabilir (fail-closed); bu bilinçli bir tasarım kararıdır.
        Optional<?> result = sourceSupplier.get();

        // Cache warming (pozitif veya negatif)
        try {
            if (result.isPresent()) {
                redisTemplate.opsForValue().set(redisKey, POSITIVE, TTL);
                log.debug("Cache ısındı (pozitif): {} (TTL={})", redisKey, TTL);
            } else {
                redisTemplate.opsForValue().set(redisKey, NEGATIVE, negativeTtl);
                log.debug("Cache ısındı (negatif): {} (TTL={})", redisKey, negativeTtl);
            }
        } catch (DataAccessException e) {
            log.warn("Redis yazma hatası (cache warming atlandı): key={}", redisKey);
        }

        return result.isPresent();
    }
}
