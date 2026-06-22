package com.tav.FlightService.mapper;

import com.tav.FlightService.domain.Flight;
import com.tav.FlightService.dto.CreateFlightRequest;
import com.tav.FlightService.dto.FlightResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface FlightMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "flightDate", expression = "java(req.scheduledDeparture().toLocalDate())")
    Flight toEntity(CreateFlightRequest req);

    FlightResponse toResponse(Flight flight);
}
