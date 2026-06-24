package com.tav.FlightService.service;

import com.tav.FlightService.audit.Auditable;
import com.tav.FlightService.common.SecurityUtils;
import com.tav.FlightService.common.exception.BusinessException;
import com.tav.FlightService.domain.Flight;
import com.tav.FlightService.dto.CreateFlightRequest;
import com.tav.FlightService.dto.FlightResponse;
import com.tav.FlightService.dto.UpdateFlightRequest;
import com.tav.FlightService.events.FlightChangeType;
import com.tav.FlightService.events.FlightEventPublisher;
import com.tav.FlightService.exception.FlightBusinessException;
import com.tav.FlightService.exception.FlightConflictException;
import com.tav.FlightService.exception.NotFoundException;
import com.tav.FlightService.exception.ReferenceNotFoundException;
import com.tav.FlightService.mapper.FlightMapper;
import com.tav.FlightService.repository.FlightRepository;
import com.tav.FlightService.validation.ReferenceValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class FlightService {

    private final FlightRepository flightRepository;
    private final FlightMapper flightMapper;
    private final ReferenceValidator referenceValidator;
    private final FlightEventPublisher flightEventPublisher;
    private final StringRedisTemplate stringRedisTemplate;

    public FlightService(FlightRepository flightRepository,
                         FlightMapper flightMapper,
                         ReferenceValidator referenceValidator,
                         FlightEventPublisher flightEventPublisher,
                         StringRedisTemplate stringRedisTemplate) {
        this.flightRepository = flightRepository;
        this.flightMapper = flightMapper;
        this.referenceValidator = referenceValidator;
        this.flightEventPublisher = flightEventPublisher;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // ------------------------------------------------------------------ READ

    /**
     * Tüm uçuşları döndürür. Büyük veri setlerinde ileride sayfalamaya alınabilir.
     */
    @Transactional(readOnly = true)
    public List<FlightResponse> findAll() {
        return flightRepository.findAll()
                .stream()
                .map(flightMapper::toResponse)
                .toList();
    }

    /**
     * Belirtilen tarihe ait uçuşları döndürür.
     */
    @Transactional(readOnly = true)
    public List<FlightResponse> findByFlightDate(LocalDate flightDate) {
        return flightRepository.findByFlightDate(flightDate)
                .stream()
                .map(flightMapper::toResponse)
                .toList();
    }

    /**
     * ID ile tekil uçuş arar; bulunamazsa {@link NotFoundException} fırlatır.
     */
    @Transactional(readOnly = true)
    public FlightResponse findById(Long id) {
        Flight flight = flightRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Uçuş bulunamadı: " + id));
        return flightMapper.toResponse(flight);
    }

    /**
     * Redis ZSET'ten kalkış saatine göre sıralı ilk {@code limit} uçuş ID'sini döndürür.
     * Redis erişilemezse boş liste döner (fail-open).
     * Kullanım: ana ekran "Yaklaşan Uçuşlar" widget'ı için MySQL ORDER BY yerine.
     */
    @Transactional(readOnly = true)
    public List<FlightResponse> getFlightsSortedByDeparture(int limit) {
        try {
            Set<String> ids = stringRedisTemplate.opsForZSet()
                    .range("flights:sorted", 0, limit - 1);
            if (ids == null || ids.isEmpty()) {
                return Collections.emptyList();
            }
            List<FlightResponse> result = new ArrayList<>();
            for (String idStr : ids) {
                flightRepository.findById(Long.parseLong(idStr))
                        .map(flightMapper::toResponse)
                        .ifPresent(result::add);
            }
            return result;
        } catch (Exception e) {
            log.warn("ZSET okuma başarısız (fail-open): hata={}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ----------------------------------------------------------------- WRITE

    @Auditable(action = "FLIGHT_CREATED")
    @Transactional
    public FlightResponse create(CreateFlightRequest request) {
        validateBusinessRules(request);
        validateReferences(request);

        Flight flight = flightMapper.toEntity(request);
        try {
            flight = flightRepository.saveAndFlush(flight);
        } catch (DataIntegrityViolationException ex) {
            throw new FlightConflictException(
                "Bu uçuş numarası ve kalkış zamanı kombinasyonu zaten mevcut: "
                + request.flightNumber() + " / " + request.scheduledDeparture()
            );
        }
        zsetAdd(flight.getId(), flight.getScheduledDeparture());
        FlightResponse response = flightMapper.toResponse(flight);
        flightEventPublisher.publish(FlightChangeType.CREATED, response, SecurityUtils.currentUsername());
        return response;
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

    @Auditable(action = "FLIGHT_UPDATED")
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
        FlightResponse response = flightMapper.toResponse(flightRepository.saveAndFlush(flight));
        zsetAdd(flight.getId(), flight.getScheduledDeparture());
        flightEventPublisher.publish(FlightChangeType.UPDATED, response, SecurityUtils.currentUsername());
        return response;
    }

    @Auditable(action = "FLIGHT_DELETED")
    @Transactional
    public void delete(Long id) {
        Flight flight = flightRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Uçuş bulunamadı: " + id));
        // Silmeden ÖNCE son state'i yakala
        FlightResponse lastKnownState = flightMapper.toResponse(flight);
        flightRepository.delete(flight);
        zsetRemove(id);
        flightEventPublisher.publish(FlightChangeType.DELETED, lastKnownState, SecurityUtils.currentUsername());
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

    // --------------------------------------------------------------- PRIVATE

    /**
     * ZSET'e uçuş ekler/günceller (score = scheduledDeparture epoch second).
     * Redis hatasında fail-open: log.warn yaz, exception fırlatma.
     */
    private void zsetAdd(Long flightId, LocalDateTime scheduledDeparture) {
        try {
            double score = scheduledDeparture.toEpochSecond(ZoneOffset.UTC);
            stringRedisTemplate.opsForZSet()
                    .add("flights:sorted", flightId.toString(), score);
        } catch (Exception e) {
            log.warn("ZSET güncelleme başarısız (fail-open): flightId={}, hata={}", flightId, e.getMessage());
        }
    }

    /**
     * ZSET'ten uçuşu kaldırır.
     * Redis hatasında fail-open: log.warn yaz, exception fırlatma.
     */
    private void zsetRemove(Long flightId) {
        try {
            stringRedisTemplate.opsForZSet()
                    .remove("flights:sorted", flightId.toString());
        } catch (Exception e) {
            log.warn("ZSET silme başarısız (fail-open): flightId={}, hata={}", flightId, e.getMessage());
        }
    }
}
