package com.tav.FlightService.repository;

import com.tav.FlightService.domain.Flight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface FlightRepository extends JpaRepository<Flight, Long> {

    boolean existsByFlightNumberAndScheduledDeparture(String flightNumber, LocalDateTime scheduledDeparture);
}
