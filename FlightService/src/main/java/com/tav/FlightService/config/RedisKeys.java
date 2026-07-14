package com.tav.FlightService.config;

// FIX: TD-011 — hardcoded Redis anahtar string'leri bu sınıfta sabit olarak toplanır.
/**
 * FlightService içinde kullanılan Redis anahtar sabitleri.
 *
 * <p>Tüm key üretimi bu sınıf üzerinden yapılmalıdır;
 * magic string dağılmasını önler ve key şemasını tek yerden yönetir.
 */
public final class RedisKeys {

    /** Kalkış saatine göre sıralı uçuşlar ZSET anahtarı. */
    public static final String FLIGHTS_SORTED = "flights:sorted";

    /** Referans varlıkları için ortak prefix. */
    public static final String REFERENCE_PREFIX = "reference:";

    public static String airline(String iata) {
        return REFERENCE_PREFIX + "AIRLINE:" + iata;
    }

    public static String aircraft(String tailNumber) {
        return REFERENCE_PREFIX + "AIRCRAFT:" + tailNumber;
    }

    public static String station(String icao) {
        return REFERENCE_PREFIX + "STATION:" + icao;
    }

    public static String route(String originIcao, String destinationIcao) {
        return REFERENCE_PREFIX + "ROUTE:" + originIcao + "-" + destinationIcao;
    }

    /**
     * FIX: TD-011 — ReferenceChangedEvent'ten Redis anahtarı üretir.
     *
     * <p>ReferenceCacheSyncListener bu metodu kullanarak hardcoded string
     * birleştirmesinden kaçınır. Kritik: businessKey formatı CachingReferenceValidator
     * ile birebir aynı olmalıdır (cache invalidation çalışsın).
     *
     * <p>Route için businessKey = "LTFM-LTAC" (originIcao + "-" + destIcao),
     * RouteService.buildRouteKey() metodu ile uyumludur.
     */
    public static String fromEvent(String entityType, String businessKey) {
        return REFERENCE_PREFIX + entityType + ":" + businessKey;
    }

    private RedisKeys() {
        // statik yardımcı sınıf; örneklenemez
    }
}
