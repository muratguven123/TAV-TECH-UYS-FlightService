package com.tav.FlightService.client;

import com.tav.FlightService.client.dto.AircraftDto;
import com.tav.FlightService.client.dto.AirlineDto;
import com.tav.FlightService.client.dto.RouteDto;
import com.tav.FlightService.client.dto.StationDto;

import java.util.Optional;

public interface ReferenceManagerClient {
    Optional<AirlineDto> getAirline(String iataCode);
    Optional<AircraftDto> getAircraft(String tailNumber);
    Optional<StationDto> getStation(String icao);
    Optional<RouteDto> getRoute(String originIcao, String destinationIcao);
}
