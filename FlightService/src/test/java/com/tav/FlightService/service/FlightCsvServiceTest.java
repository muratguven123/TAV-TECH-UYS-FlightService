package com.tav.FlightService.service;

import com.tav.FlightService.domain.Flight;
import com.tav.FlightService.domain.FlightType;
import com.tav.FlightService.dto.BulkUploadResult;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlightCsvServiceTest {

    @Mock FlightRepository flightRepository;
    @Mock FlightMapper flightMapper;
    @Mock FlightService flightService;
    @Mock Validator validator;

    @InjectMocks
    FlightCsvService csvService;

    private static final String CSV_HEADER =
            "flightNumber,airlineCode,aircraftTailNumber,originStation,destinationStation,flightType,scheduledDeparture,scheduledArrival\n";

    private static final String VALID_ROW =
            "TK001,TK,TC-JFA,LTFM,LTAC,PASSENGER,2027-06-23T10:00:00,2027-06-23T11:30:00\n";

    @Test
    @DisplayName("parseCsv: 3 geçerli satır içeren CSV parse edilince FlightRepository 3 kayıt alır")
    void parseCsv_validRows_savesAll() throws IOException {
        // given
        String csv = CSV_HEADER + VALID_ROW + VALID_ROW + VALID_ROW;
        MultipartFile file = new MockMultipartFile("file", "flights.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        Flight mappedFlight = TestDataFactory.buildFlight();
        when(validator.validate(any())).thenReturn(Set.of());
        doNothing().when(flightService).validateBusinessRules(any());
        doNothing().when(flightService).validateReferences(any());
        when(flightRepository.existsByFlightNumberAndScheduledDeparture(anyString(), any()))
                .thenReturn(false);
        when(flightMapper.toEntity(any())).thenReturn(mappedFlight);
        when(flightRepository.saveAll(anyList())).thenReturn(List.of(mappedFlight, mappedFlight, mappedFlight));

        // when
        BulkUploadResult result = csvService.upload(file);

        // then
        assertThat(result.successCount()).isEqualTo(3);
        assertThat(result.failureCount()).isEqualTo(0);
        verify(flightRepository).saveAll(argThat(list -> ((List<?>) list).size() == 3));
    }

    @Test
    @DisplayName("parseCsv: validation hatası olan satır atlanır ve raporda görünür")
    void parseCsv_invalidRow_skipsAndCollectsError() throws IOException {
        // given — geçerli 1 + geçersiz 1 satır (sütun sayısı yetersiz)
        String invalidRow = "BAD_ROW_ONLY_3_COLS,TK,TC-JFA\n";
        String csv = CSV_HEADER + VALID_ROW + invalidRow;
        MultipartFile file = new MockMultipartFile("file", "flights.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        Flight mappedFlight = TestDataFactory.buildFlight();
        when(validator.validate(any())).thenReturn(Set.of());
        doNothing().when(flightService).validateBusinessRules(any());
        doNothing().when(flightService).validateReferences(any());
        when(flightRepository.existsByFlightNumberAndScheduledDeparture(anyString(), any()))
                .thenReturn(false);
        when(flightMapper.toEntity(any())).thenReturn(mappedFlight);
        when(flightRepository.saveAll(anyList())).thenReturn(List.of(mappedFlight));

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
        when(flightRepository.saveAll(anyList())).thenReturn(List.of());

        // when
        BulkUploadResult result = csvService.upload(file);

        // then
        assertThat(result.successCount()).isEqualTo(0);
        assertThat(result.failureCount()).isEqualTo(0);
        assertThat(result.totalRows()).isEqualTo(0);
    }

    @Test
    @DisplayName("parseCsv: hatalı tarih formatı olan satır hata listesine eklenir")
    void parseCsv_malformedDate_skipsRowWithError() throws IOException {
        // given — scheduledDeparture geçersiz format
        String badDateRow = "TK001,TK,TC-JFA,LTFM,LTAC,PASSENGER,NOT-A-DATE,2027-06-23T11:30:00\n";
        String csv = CSV_HEADER + badDateRow;
        MultipartFile file = new MockMultipartFile("file", "flights.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        when(flightRepository.saveAll(anyList())).thenReturn(List.of());

        // when
        BulkUploadResult result = csvService.upload(file);

        // then
        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(result.errors().get(0).error()).contains("scheduledDeparture");
    }
}
