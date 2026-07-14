package com.tav.FlightService.service;

// FIX: DEF-003 — saveAll() yerine flightService.create() kullanımı test edildi.
import com.tav.FlightService.domain.Flight;
import com.tav.FlightService.dto.BulkUploadResult;
import com.tav.FlightService.dto.CreateFlightRequest;
import com.tav.FlightService.dto.FlightResponse;
import com.tav.FlightService.exception.FlightConflictException;
import com.tav.FlightService.mapper.FlightMapper;
import com.tav.FlightService.repository.FlightRepository;
import com.tav.FlightService.util.TestDataFactory;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("FlightCsvService — Bulk CSV upload birim testleri")
@ExtendWith(MockitoExtension.class)
class FlightCsvServiceTest {

    @Mock FlightRepository flightRepository;
    @Mock FlightMapper flightMapper;  // constructor'da geçiyor, kullanılmıyor ama mock gerekli
    @Mock FlightService flightService;
    @Mock Validator validator;
    @Mock SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    FlightCsvService csvService;

    private static final String CSV_HEADER =
            "flightNumber,airlineCode,aircraftTailNumber,originStation,destinationStation,flightType,scheduledDeparture,scheduledArrival\n";

    private static final String VALID_ROW =
            "TK001,TK,TC-JFA,LTFM,LTAC,PASSENGER,2027-06-23T10:00:00,2027-06-23T11:30:00\n";

    // ------------------------------------------------------------------ DEF-003 testleri

    @Test
    @DisplayName("parseCsv: 3 geçerli satır için flightService.create 3 kez çağrılır")
    void parseCsv_validRows_invokesFlightServiceCreatePerRow() throws IOException {
        // given
        String csv = CSV_HEADER + VALID_ROW + VALID_ROW + VALID_ROW;
        MultipartFile file = new MockMultipartFile("file", "flights.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        FlightResponse mockResponse = TestDataFactory.buildFlightResponse();
        when(validator.validate(any())).thenReturn(Set.of());
        when(flightService.create(any(CreateFlightRequest.class))).thenReturn(mockResponse);

        // when
        BulkUploadResult result = csvService.upload(file);

        // then — FIX: DEF-003 saveAll DEĞİL create 3 kez çağrılmalı
        verify(flightService, times(3)).create(any(CreateFlightRequest.class));
        assertThat(result.successCount()).isEqualTo(3);
        assertThat(result.failureCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("parseCsv: 1 invalid satır + 2 geçerli satır; create 2 kez çağrılır, errors 1 satır içerir")
    void parseCsv_oneRowFails_othersStillCreated() throws IOException {
        // given
        String invalidRow = "BAD_ROW_ONLY_3_COLS,TK,TC-JFA\n";
        String csv = CSV_HEADER + VALID_ROW + invalidRow + VALID_ROW;
        MultipartFile file = new MockMultipartFile("file", "flights.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        FlightResponse mockResponse = TestDataFactory.buildFlightResponse();
        when(validator.validate(any())).thenReturn(Set.of());
        when(flightService.create(any(CreateFlightRequest.class))).thenReturn(mockResponse);

        // when
        BulkUploadResult result = csvService.upload(file);

        // then
        verify(flightService, times(2)).create(any(CreateFlightRequest.class));
        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(result.errors()).hasSize(1);
    }

    @Test
    @DisplayName("parseCsv: flightService.create FlightConflictException fırlatınca satır error listesine eklenir, diğerleri etkilenmez")
    void parseCsv_flightServiceCreateThrowsConflict_recordedAsRowError() throws IOException {
        // given — 3 satır; ikincisi çakışma hatası verir
        String csv = CSV_HEADER + VALID_ROW + VALID_ROW + VALID_ROW;
        MultipartFile file = new MockMultipartFile("file", "flights.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        FlightResponse mockResponse = TestDataFactory.buildFlightResponse();
        when(validator.validate(any())).thenReturn(Set.of());
        when(flightService.create(any(CreateFlightRequest.class)))
                .thenReturn(mockResponse)                           // 1. satır başarılı
                .thenThrow(new FlightConflictException("Duplicate")) // 2. satır hata
                .thenReturn(mockResponse);                          // 3. satır başarılı

        // when
        BulkUploadResult result = csvService.upload(file);

        // then — 2 başarı, 1 hata; tüm satırlar işlendi
        verify(flightService, times(3)).create(any(CreateFlightRequest.class));
        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(result.errors().get(0).error()).containsIgnoringCase("Duplicate");
    }

    // ------------------------------------------------------------------ mevcut testler (uyumlandı)

    @Test
    @DisplayName("parseCsv: validation hatası olan satır atlanır ve raporda görünür")
    void parseCsv_invalidRow_skipsAndCollectsError() throws IOException {
        // given — geçerli 1 + geçersiz 1 satır (sütun sayısı yetersiz)
        String invalidRow = "BAD_ROW_ONLY_3_COLS,TK,TC-JFA\n";
        String csv = CSV_HEADER + VALID_ROW + invalidRow;
        MultipartFile file = new MockMultipartFile("file", "flights.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        FlightResponse mockResponse = TestDataFactory.buildFlightResponse();
        when(validator.validate(any())).thenReturn(Set.of());
        when(flightService.create(any(CreateFlightRequest.class))).thenReturn(mockResponse);

        // when
        BulkUploadResult result = csvService.upload(file);

        // then
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(result.errors()).hasSize(1);
    }

    @Test
    @DisplayName("parseCsv: boş dosya yüklenince boş sonuç döner")
    void parseCsv_emptyFile_returnsEmptyResult() throws IOException {
        // given — sadece header, veri satırı yok
        String csv = CSV_HEADER;
        MultipartFile file = new MockMultipartFile("file", "flights.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        // when
        BulkUploadResult result = csvService.upload(file);

        // then
        assertThat(result.successCount()).isEqualTo(0);
        assertThat(result.failureCount()).isEqualTo(0);
        assertThat(result.totalRows()).isEqualTo(0);
        verify(flightService, never()).create(any());
    }

    @Test
    @DisplayName("parseCsv: hatalı tarih formatı olan satır hata listesine eklenir")
    void parseCsv_malformedDate_skipsRowWithError() throws IOException {
        // given — scheduledDeparture geçersiz format
        String badDateRow = "TK001,TK,TC-JFA,LTFM,LTAC,PASSENGER,NOT-A-DATE,2027-06-23T11:30:00\n";
        String csv = CSV_HEADER + badDateRow;
        MultipartFile file = new MockMultipartFile("file", "flights.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        // when
        BulkUploadResult result = csvService.upload(file);

        // then
        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(result.errors().get(0).error()).contains("scheduledDeparture");
        verify(flightService, never()).create(any());
    }

    @Test
    @DisplayName("processAsync → progress ve summary WebSocket topic'ine gönderilir")
    void processAsync_sendsProgressAndCompletedMessages() throws IOException {
        String csv = CSV_HEADER + VALID_ROW + VALID_ROW;
        byte[] content = csv.getBytes(StandardCharsets.UTF_8);

        when(validator.validate(any(CreateFlightRequest.class))).thenReturn(Set.of());
        when(flightService.create(any(CreateFlightRequest.class)))
                .thenReturn(TestDataFactory.buildFlightResponse());

        csvService.processAsync("job-1", content);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/bulk.job-1"), payloadCaptor.capture());

        List<Map<String, Object>> payloads = payloadCaptor.getAllValues();
        assertThat(payloads.stream().anyMatch(m -> "PROGRESS".equals(m.get("status")))).isTrue();
        assertThat(payloads.stream().anyMatch(m ->
                "COMPLETED".equals(m.get("status")) && Integer.valueOf(2).equals(m.get("totalRows")))).isTrue();
    }
}
