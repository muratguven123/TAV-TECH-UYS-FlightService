package com.tav.FlightService.repository;

import com.tav.FlightService.domain.Flight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FlightRepository extends JpaRepository<Flight, Long> {

    boolean existsByFlightNumberAndScheduledDeparture(String flightNumber, LocalDateTime scheduledDeparture);

    /** Belirtilen uçuş tarihine ait tüm uçuşları döndürür. */
    List<Flight> findByFlightDate(LocalDate flightDate);

    /** Belirtilen havayolu koduna ait tüm uçuşları döndürür. */
    List<Flight> findByAirlineCode(String airlineCode);
}
