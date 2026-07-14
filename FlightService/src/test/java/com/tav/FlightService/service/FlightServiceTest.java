package com.tav.FlightService.service;

import com.tav.FlightService.domain.Flight;
import com.tav.FlightService.dto.CreateFlightRequest;
import com.tav.FlightService.dto.FlightResponse;
import com.tav.FlightService.dto.UpdateFlightRequest;
import com.tav.uys.events.FlightChangeType;
import com.tav.FlightService.events.FlightEventPublisher;
import com.tav.FlightService.exception.FlightBusinessException;
import com.tav.FlightService.exception.NotFoundException;
import com.tav.FlightService.mapper.FlightMapper;
import com.tav.FlightService.repository.FlightRepository;
import com.tav.FlightService.util.TestDataFactory;
import com.tav.FlightService.validation.ReferenceValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlightServiceTest {

    @Mock FlightRepository flightRepository;
    @Mock FlightMapper flightMapper;
    @Mock ReferenceValidator referenceValidator;
    @Mock FlightEventPublisher flightEventPublisher;
    @Mock StringRedisTemplate stringRedisTemplate;
    @Mock ZSetOperations<String, String> zSetOperations;

    @InjectMocks
    FlightService flightService;

    private Flight flight;
    private FlightResponse flightResponse;

    @BeforeEach
    void setUp() {
        flight = TestDataFactory.buildFlight();
        flightResponse = TestDataFactory.buildFlightResponse();
        lenient().when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
    }

    // ------------------------------------------------------------------ findAll

    @Test
    @DisplayName("findAll: filtre yoksa tüm uçuşlar döner")
    void findAll_whenNoFilter_returnsAllFlights() {
        // given
        Flight flight2 = TestDataFactory.buildFlight();
        flight2.setId(2L);
        FlightResponse response2 = TestDataFactory.buildFlightResponse();

        when(flightRepository.findAll()).thenReturn(List.of(flight, flight2));
        when(flightMapper.toResponse(flight)).thenReturn(flightResponse);
        when(flightMapper.toResponse(flight2)).thenReturn(response2);

        // when
        List<FlightResponse> result = flightService.findAll();

        // then
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("findAll: repository boşsa boş liste döner")
    void findAll_whenEmpty_returnsEmptyList() {
        // given
        when(flightRepository.findAll()).thenReturn(List.of());

        // when
        List<FlightResponse> result = flightService.findAll();

        // then
        assertThat(result).isEmpty();
    }

    // ----------------------------------------------------------- findByFlightDate

    @Test
    @DisplayName("findByFlightDate: verilen tarih için repository filtreli çağrılır")
    void findByFlightDate_returnsFilteredList() {
        // given
        LocalDate date = TestDataFactory.FLIGHT_DATE;
        when(flightRepository.findByFlightDate(date)).thenReturn(List.of(flight));
        when(flightMapper.toResponse(flight)).thenReturn(flightResponse);

        // when
        List<FlightResponse> result = flightService.findByFlightDate(date);

        // then
        assertThat(result).hasSize(1);
        verify(flightRepository).findByFlightDate(date);
    }

    // -------------------------------------------------------------- findById

    @Test
    @DisplayName("findById: kayıt varsa response döner")
    void findById_whenExists_returnsResponse() {
        // given
        when(flightRepository.findById(1L)).thenReturn(Optional.of(flight));
        when(flightMapper.toResponse(flight)).thenReturn(flightResponse);

        // when
        FlightResponse result = flightService.findById(1L);

        // then
        assertThat(result).isEqualTo(flightResponse);
    }

    @Test
    @DisplayName("findById: kayıt yoksa NotFoundException fırlatılır")
    void findById_whenNotFound_throwsNotFoundException() {
        // given
        when(flightRepository.findById(99L)).thenReturn(Optional.empty());

        // when / then
        assertThrows(NotFoundException.class, () -> flightService.findById(99L));
    }

    // ------------------------------------------------------------------ create

    @Test
    @DisplayName("create: başarılı yolda kaydet, event yayınla, ZSET güncelle")
    void create_happyPath_savesAndPublishesEventAndUpdatesZset() {
        // given
        CreateFlightRequest req = TestDataFactory.buildCreateRequest();
        when(referenceValidator.airlineExists(anyString())).thenReturn(true);
        when(referenceValidator.aircraftExists(anyString())).thenReturn(true);
        when(referenceValidator.stationExists(anyString())).thenReturn(true);
        when(referenceValidator.routeExists(anyString(), anyString())).thenReturn(true);
        when(flightMapper.toEntity(req)).thenReturn(flight);
        when(flightRepository.saveAndFlush(flight)).thenReturn(flight);
        when(flightMapper.toResponse(flight)).thenReturn(flightResponse);

        // when
        FlightResponse result = flightService.create(req);

        // then
        verify(flightRepository).saveAndFlush(flight);
        verify(flightEventPublisher).publish(eq(FlightChangeType.CREATED), eq(flightResponse), any());
        verify(zSetOperations).add(eq("flights:sorted"), eq("1"), anyDouble());
        assertThat(result).isEqualTo(flightResponse);
    }

    @Test
    @DisplayName("create: Redis fırlatınca kayıt ve event yine de gerçekleşir (fail-open)")
    void create_whenRedisThrows_stillSavesAndPublishes() {
        // given
        CreateFlightRequest req = TestDataFactory.buildCreateRequest();
        when(referenceValidator.airlineExists(anyString())).thenReturn(true);
        when(referenceValidator.aircraftExists(anyString())).thenReturn(true);
        when(referenceValidator.stationExists(anyString())).thenReturn(true);
        when(referenceValidator.routeExists(anyString(), anyString())).thenReturn(true);
        when(flightMapper.toEntity(req)).thenReturn(flight);
        when(flightRepository.saveAndFlush(flight)).thenReturn(flight);
        when(flightMapper.toResponse(flight)).thenReturn(flightResponse);
        when(zSetOperations.add(anyString(), anyString(), anyDouble()))
                .thenThrow(new RuntimeException("Redis bağlantı hatası"));

        // when — exception fırlatmamalı (fail-open)
        FlightResponse result = flightService.create(req);

        // then
        verify(flightRepository).saveAndFlush(flight);
        verify(flightEventPublisher).publish(eq(FlightChangeType.CREATED), any(), any());
        assertThat(result).isNotNull();
    }

    // ------------------------------------------------------------------ update

    @Test
    @DisplayName("update: başarılı yolda günceller, event yayınlar, ZSET günceller")
    void update_happyPath_updatesAndPublishesEventAndUpdatesZset() {
        // given
        UpdateFlightRequest req = TestDataFactory.buildUpdateRequest();
        when(flightRepository.findById(1L)).thenReturn(Optional.of(flight));
        when(flightRepository.saveAndFlush(flight)).thenReturn(flight);
        when(flightMapper.toResponse(flight)).thenReturn(flightResponse);

        // when
        FlightResponse result = flightService.update(1L, req);

        // then
        verify(flightRepository).saveAndFlush(flight);
        verify(flightEventPublisher).publish(eq(FlightChangeType.UPDATED), any(), any());
        verify(zSetOperations).add(eq("flights:sorted"), eq("1"), anyDouble());
        assertThat(result).isEqualTo(flightResponse);
    }

    @Test
    @DisplayName("update: varış kalkıştan önce ise FlightBusinessException fırlatılır")
    void update_whenArrivalBeforeDeparture_throwsBusinessException() {
        // FIX: DEF-013 — ortak helper ile aynı kural create ve update'te geçerli
        // given
        LocalDateTime departure = LocalDateTime.of(2027, 6, 23, 12, 0);
        LocalDateTime arrival   = LocalDateTime.of(2027, 6, 23, 10, 0); // geride
        UpdateFlightRequest req = new UpdateFlightRequest(departure, arrival,
                com.tav.FlightService.domain.FlightType.PASSENGER);
        when(flightRepository.findById(1L)).thenReturn(Optional.of(flight));

        // when / then
        assertThatThrownBy(() -> flightService.update(1L, req))
                .isInstanceOf(com.tav.FlightService.exception.FlightBusinessException.class)
                .hasMessageContaining("varış");
        verify(flightRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("update: kayıt yoksa exception fırlatılır")
    void update_whenNotFound_throwsNotFoundException() {
        // given
        when(flightRepository.findById(99L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> flightService.update(99L, TestDataFactory.buildUpdateRequest()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("update: Redis fırlatınca yine de güncelleme gerçekleşir (fail-open)")
    void update_whenRedisThrows_stillUpdates() {
        // given
        UpdateFlightRequest req = TestDataFactory.buildUpdateRequest();
        when(flightRepository.findById(1L)).thenReturn(Optional.of(flight));
        when(flightRepository.saveAndFlush(flight)).thenReturn(flight);
        when(flightMapper.toResponse(flight)).thenReturn(flightResponse);
        when(zSetOperations.add(anyString(), anyString(), anyDouble()))
                .thenThrow(new RuntimeException("Redis bağlantı hatası"));

        // when — exception fırlatmamalı
        FlightResponse result = flightService.update(1L, req);

        // then
        verify(flightRepository).saveAndFlush(flight);
        assertThat(result).isNotNull();
    }

    // ------------------------------------------------------------------ delete

    @Test
    @DisplayName("delete: başarılı yolda siler, event yayınlar, ZSET'ten kaldırır")
    void delete_happyPath_deletesAndPublishesEventAndRemovesFromZset() {
        // given
        when(flightRepository.findById(1L)).thenReturn(Optional.of(flight));
        when(flightMapper.toResponse(flight)).thenReturn(flightResponse);

        // when
        flightService.delete(1L);

        // then
        verify(flightRepository).delete(flight);
        verify(flightEventPublisher).publish(eq(FlightChangeType.DELETED), eq(flightResponse), any());
        verify(zSetOperations).remove("flights:sorted", "1");
    }

    @Test
    @DisplayName("delete: kayıt yoksa exception fırlatılır")
    void delete_whenNotFound_throwsNotFoundException() {
        // given
        when(flightRepository.findById(99L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> flightService.delete(99L))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("delete: Redis fırlatınca yine de silme gerçekleşir (fail-open)")
    void delete_whenRedisThrows_stillDeletes() {
        // given
        when(flightRepository.findById(1L)).thenReturn(Optional.of(flight));
        when(flightMapper.toResponse(flight)).thenReturn(flightResponse);
        when(zSetOperations.remove(anyString(), any())).thenThrow(new RuntimeException("Redis down"));

        // when — exception fırlatmamalı
        flightService.delete(1L);

        // then
        verify(flightRepository).delete(flight);
        verify(flightEventPublisher).publish(eq(FlightChangeType.DELETED), any(), any());
    }

    // ------------------------------------------------ getFlightsSortedByDeparture

    @Test
    @DisplayName("getSortedByDeparture: ZSET'ten 2 ID gelince her ikisi DB'den alınır")
    void getSortedByDeparture_happyPath_returnsOrderedList() {
        // given
        when(zSetOperations.range("flights:sorted", 0, 1)).thenReturn(Set.of("1", "2"));
        Flight flight2 = TestDataFactory.buildFlight();
        flight2.setId(2L);
        when(flightRepository.findById(1L)).thenReturn(Optional.of(flight));
        when(flightRepository.findById(2L)).thenReturn(Optional.of(flight2));
        when(flightMapper.toResponse(flight)).thenReturn(flightResponse);
        when(flightMapper.toResponse(flight2)).thenReturn(flightResponse);

        // when
        List<FlightResponse> result = flightService.getFlightsSortedByDeparture(2);

        // then
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("getSortedByDeparture: Redis fırlatınca boş liste döner (fail-open)")
    void getSortedByDeparture_whenRedisThrows_returnsEmptyList() {
        // given
        when(zSetOperations.range(anyString(), anyLong(), anyLong()))
                .thenThrow(new RuntimeException("Redis bağlantı hatası"));

        // when
        List<FlightResponse> result = flightService.getFlightsSortedByDeparture(5);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getSortedByDeparture: ZSET'teki ID DB'de yoksa atlanır")
    void getSortedByDeparture_whenIdNotInDb_skipsGracefully() {
        // given
        when(zSetOperations.range("flights:sorted", 0, 0)).thenReturn(Set.of("999"));
        when(flightRepository.findById(999L)).thenReturn(Optional.empty());

        // when
        List<FlightResponse> result = flightService.getFlightsSortedByDeparture(1);

        // then
        assertThat(result).isEmpty();
    }

    // ----------------------------------------- validateBusinessRules (dolaylı)

    @Test
    @DisplayName("create: origin == destination ise FlightBusinessException fırlatılır")
    void create_whenOriginEqualsDestination_throwsBusinessException() {
        // given
        CreateFlightRequest req = TestDataFactory.buildCreateRequest("LTFM", "LTFM");

        // when / then
        assertThrows(FlightBusinessException.class, () -> flightService.create(req));
        verify(flightRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("create: varış kalkıştan önce ise FlightBusinessException fırlatılır")
    void create_whenArrivalBeforeDeparture_throwsBusinessException() {
        // given
        LocalDateTime departure = LocalDateTime.of(2026, 6, 23, 12, 0);
        LocalDateTime arrival   = LocalDateTime.of(2026, 6, 23, 10, 0); // geride
        CreateFlightRequest req = TestDataFactory.buildCreateRequestWithTimes(departure, arrival);

        // when / then
        assertThrows(FlightBusinessException.class, () -> flightService.create(req));
        verify(flightRepository, never()).saveAndFlush(any());
    }
}
