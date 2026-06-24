package com.tav.FlightService.util;

import com.tav.FlightService.domain.Flight;
import com.tav.FlightService.domain.FlightType;
import com.tav.FlightService.dto.CreateFlightRequest;
import com.tav.FlightService.dto.FlightResponse;
import com.tav.FlightService.dto.UpdateFlightRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class TestDataFactory {

    public static final Long    FLIGHT_ID    = 1L;
    public static final String  FLIGHT_NO    = "TK001";
    public static final String  AIRLINE_CODE = "TK";
    public static final String  TAIL_NUMBER  = "TC-JFA";
    public static final String  ORIGIN       = "LTFM";
    public static final String  DESTINATION  = "LTAC";

    public static final LocalDateTime DEPARTURE =
            LocalDateTime.of(2026, 6, 23, 10, 0);
    public static final LocalDateTime ARRIVAL   =
            LocalDateTime.of(2026, 6, 23, 11, 30);
    public static final LocalDate     FLIGHT_DATE =
            LocalDate.of(2026, 6, 23);

    public static Flight buildFlight() {
        Flight f = new Flight();
        f.setId(FLIGHT_ID);
        f.setFlightNumber(FLIGHT_NO);
        f.setAirlineCode(AIRLINE_CODE);
        f.setAircraftTailNumber(TAIL_NUMBER);
        f.setOriginStation(ORIGIN);
        f.setDestinationStation(DESTINATION);
        f.setFlightType(FlightType.PASSENGER);
        f.setScheduledDeparture(DEPARTURE);
        f.setScheduledArrival(ARRIVAL);
        f.setFlightDate(FLIGHT_DATE);
        return f;
    }

    public static FlightResponse buildFlightResponse() {
        return new FlightResponse(
                FLIGHT_ID, FLIGHT_NO, AIRLINE_CODE, TAIL_NUMBER,
                ORIGIN, DESTINATION, FlightType.PASSENGER,
                DEPARTURE, ARRIVAL, FLIGHT_DATE, 0L);
    }

    public static CreateFlightRequest buildCreateRequest() {
        return new CreateFlightRequest(
                FLIGHT_NO, AIRLINE_CODE, TAIL_NUMBER,
                ORIGIN, DESTINATION, FlightType.PASSENGER,
                DEPARTURE, ARRIVAL);
    }

    public static CreateFlightRequest buildCreateRequest(String origin, String destination) {
        return new CreateFlightRequest(
                FLIGHT_NO, AIRLINE_CODE, TAIL_NUMBER,
                origin, destination, FlightType.PASSENGER,
                DEPARTURE, ARRIVAL);
    }

    public static CreateFlightRequest buildCreateRequestWithTimes(LocalDateTime departure,
                                                                   LocalDateTime arrival) {
        return new CreateFlightRequest(
                FLIGHT_NO, AIRLINE_CODE, TAIL_NUMBER,
                ORIGIN, DESTINATION, FlightType.PASSENGER,
                departure, arrival);
    }

    public static UpdateFlightRequest buildUpdateRequest() {
        return new UpdateFlightRequest(DEPARTURE, ARRIVAL, FlightType.PASSENGER);
    }
}
