package com.tav.FlightService.domain;

import com.tav.FlightService.common.BaseEntity;
import com.tav.FlightService.common.validation.ValidFlightNumber;
import com.tav.FlightService.common.validation.ValidIcao;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
    name = "flights",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_flight_no_dep",
        columnNames = {"flight_number", "scheduled_departure"}
    ),
    indexes = {
        @Index(name = "idx_flight_date", columnList = "flight_date"),
        @Index(name = "idx_flight_number", columnList = "flight_number")
    }
)
public class Flight extends BaseEntity {

    @Column(name = "flight_number", nullable = false, length = 6)
    @ValidFlightNumber
    @NotBlank
    private String flightNumber;

    @Column(name = "airline_code", nullable = false, length = 2)
    @NotBlank
    @Size(min = 2, max = 2)
    private String airlineCode;

    @Column(name = "aircraft_tail_number", nullable = false, length = 10)
    @NotBlank
    private String aircraftTailNumber;

    @Column(name = "origin_station", nullable = false, length = 4)
    @ValidIcao
    @NotBlank
    private String originStation;

    @Column(name = "destination_station", nullable = false, length = 4)
    @ValidIcao
    @NotBlank
    private String destinationStation;

    @Enumerated(EnumType.STRING)
    @Column(name = "flight_type", nullable = false, length = 20)
    @NotNull
    private FlightType flightType;

    // @Future KALDIRILDI: sadece CreateFlightRequest DTO'sunda kalmalı.
    // Entity pre-update lifecycle'ında geçmiş tarihli uçuşlara güncelleme yapılırken
    // (Aşama 5 optimistic locking) ConstraintViolationException fırlatırdı.
    @Column(name = "scheduled_departure", nullable = false)
    @NotNull
    private LocalDateTime scheduledDeparture;

    @Column(name = "scheduled_arrival", nullable = false)
    @NotNull
    private LocalDateTime scheduledArrival;

    @Column(name = "flight_date", nullable = false)
    @NotNull
    private LocalDate flightDate;
}
