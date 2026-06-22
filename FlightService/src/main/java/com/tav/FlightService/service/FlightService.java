package com.tav.FlightService.service;

import com.tav.FlightService.common.exception.BusinessException;
import com.tav.FlightService.domain.Flight;
import com.tav.FlightService.dto.CreateFlightRequest;
import com.tav.FlightService.dto.FlightResponse;
import com.tav.FlightService.dto.UpdateFlightRequest;
import com.tav.FlightService.exception.FlightBusinessException;
import com.tav.FlightService.exception.FlightConflictException;
import com.tav.FlightService.exception.ReferenceNotFoundException;
import com.tav.FlightService.mapper.FlightMapper;
import com.tav.FlightService.repository.FlightRepository;
import com.tav.FlightService.validation.ReferenceValidator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FlightService {

    private final FlightRepository flightRepository;
    private final FlightMapper flightMapper;
    private final ReferenceValidator referenceValidator;

    public FlightService(FlightRepository flightRepository,
                         FlightMapper flightMapper,
                         ReferenceValidator referenceValidator) {
        this.flightRepository = flightRepository;
        this.flightMapper = flightMapper;
        this.referenceValidator = referenceValidator;
    }

    @Transactional
    public FlightResponse create(CreateFlightRequest request) {
        validateBusinessRules(request);
        validateReferences(request);

        Flight flight = flightMapper.toEntity(request);
        try {
            flight = flightRepository.save(flight);
        } catch (DataIntegrityViolationException ex) {
            throw new FlightConflictException(
                "Bu uçuş numarası ve kalkış zamanı kombinasyonu zaten mevcut: "
                + request.flightNumber() + " / " + request.scheduledDeparture()
            );
        }
        return flightMapper.toResponse(flight);
    }

    public void validateBusinessRules(CreateFlightRequest request) {
        if (request.originStation().equalsIgnoreCase(request.destinationStation())) {
            throw new FlightBusinessException(
                "Kalkış istasyonu ile varış istasyonu aynı olamaz: " + request.originStation()
            );
        }
        if (!request.scheduledArrival().isAfter(request.scheduledDeparture())) {
            throw new FlightBusinessException(
                "Planlanan varış zamanı, kalkış zamanından sonra olmalıdır."
            );
        }
    }

    @Transactional
    public FlightResponse update(Long id, UpdateFlightRequest req) {
        Flight flight = flightRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Uçuş bulunamadı: " + id));
        if (!req.scheduledArrival().isAfter(req.scheduledDeparture())) {
            throw new BusinessException("Varış zamanı kalkıştan sonra olmalı");
        }
        flight.setScheduledDeparture(req.scheduledDeparture());
        flight.setScheduledArrival(req.scheduledArrival());
        flight.setFlightType(req.flightType());
        flight.setFlightDate(req.scheduledDeparture().toLocalDate());
        return flightMapper.toResponse(flightRepository.save(flight));
    }

    @Transactional
    public void delete(Long id) {
        Flight flight = flightRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Uçuş bulunamadı: " + id));
        flightRepository.delete(flight);
    }

    public void validateReferences(CreateFlightRequest request) {
        if (!referenceValidator.airlineExists(request.airlineCode())) {
            throw new ReferenceNotFoundException("Havayolu bulunamadı: " + request.airlineCode());
        }
        if (!referenceValidator.aircraftExists(request.aircraftTailNumber())) {
            throw new ReferenceNotFoundException("Uçak bulunamadı: " + request.aircraftTailNumber());
        }
        if (!referenceValidator.stationExists(request.originStation())) {
            throw new ReferenceNotFoundException("Kalkış istasyonu bulunamadı: " + request.originStation());
        }
        if (!referenceValidator.stationExists(request.destinationStation())) {
            throw new ReferenceNotFoundException("Varış istasyonu bulunamadı: " + request.destinationStation());
        }
        if (!referenceValidator.routeExists(request.originStation(), request.destinationStation())) {
            throw new ReferenceNotFoundException(
                "Rota bulunamadı: " + request.originStation() + " -> " + request.destinationStation()
            );
        }
    }
}
