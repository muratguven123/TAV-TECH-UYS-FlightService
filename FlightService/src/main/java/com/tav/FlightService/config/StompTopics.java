package com.tav.FlightService.config;

/**
 * STOMP topic destination'ları.
 *
 * RabbitMQ STOMP relay slash içeren isimleri reddeder ({@code /flights/all} geçersiz);
 * nokta notasyonu kullanılır ({@code /topic/flights.all}).
 */
public final class StompTopics {

    public static final String FLIGHTS_ALL = "/topic/flights.all";
    public static final String FLIGHTS_AIRLINE_PREFIX = "/topic/flights.airline.";
    public static final String FLIGHTS_STATION_PREFIX = "/topic/flights.station.";
    public static final String SYSTEM_HEALTH = "/topic/system.health";
    public static final String REFERENCE_CHANGED = "/topic/reference.changed";
    public static final String BULK_PREFIX = "/topic/bulk.";

    private StompTopics() {
    }
}
