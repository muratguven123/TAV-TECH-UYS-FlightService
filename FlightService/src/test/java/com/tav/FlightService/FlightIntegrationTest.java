package com.tav.FlightService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tav.FlightService.domain.FlightType;
import com.tav.FlightService.dto.BulkUploadResult;
import com.tav.FlightService.dto.CreateFlightRequest;
import com.tav.FlightService.repository.FlightRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FlightIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FlightRepository flightRepository;

    // Dinamik tarihler: @Future validasyonu her zaman geçer, hardcoded tarih zaman bombası yok.
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /** Bugünden 30 gün sonra 10:30 */
    private LocalDateTime departure() {
        return LocalDateTime.now().plusDays(30).withHour(10).withMinute(30).withSecond(0).withNano(0);
    }

    /** Bugünden 30 gün sonra 12:00 */
    private LocalDateTime arrival() {
        return LocalDateTime.now().plusDays(30).withHour(12).withMinute(0).withSecond(0).withNano(0);
    }

    private String validRequestJson() throws Exception {
        CreateFlightRequest req = new CreateFlightRequest(
            "TK1980", "TK", "TC-JFC",
            "LTBA", "LTFM", FlightType.PASSENGER,
            departure(), arrival()
        );
        return objectMapper.writeValueAsString(req);
    }

    // ─── Happy Path ────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "officer1", roles = "OPERATION_OFFICER")
    void createFlight_happyPath_returns201AndVersionZero() throws Exception {
        mockMvc.perform(post("/api/flights")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.flightNumber").value("TK1980"))
            .andExpect(jsonPath("$.version").value(0));
    }

    // Hibernate ddl-auto:update → tablo + unique constraint otomatik oluştu
    @Test
    @WithMockUser(username = "officer1", roles = "OPERATION_OFFICER")
    void flightsTableAndUniqueConstraintExist() throws Exception {
        mockMvc.perform(post("/api/flights")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson()))
            .andExpect(status().isCreated());

        // Aynı flightNumber + scheduledDeparture → 409
        mockMvc.perform(post("/api/flights")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson()))
            .andExpect(status().isConflict());
    }

    // ─── Format Validasyonları ─────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "officer1", roles = "OPERATION_OFFICER")
    void createFlight_invalidFlightNumber_returns400() throws Exception {
        CreateFlightRequest req = new CreateFlightRequest(
            "ABCD", "TK", "TC-JFC",
            "LTBA", "LTFM", FlightType.PASSENGER,
            departure(), arrival()
        );
        mockMvc.perform(post("/api/flights")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest());
    }

    // ─── İş Kuralları ─────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "officer1", roles = "OPERATION_OFFICER")
    void createFlight_originEqualsDestination_returns400() throws Exception {
        CreateFlightRequest req = new CreateFlightRequest(
            "TK1981", "TK", "TC-JFC",
            "LTBA", "LTBA", FlightType.PASSENGER,
            departure(), arrival()
        );
        mockMvc.perform(post("/api/flights")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "officer1", roles = "OPERATION_OFFICER")
    void createFlight_arrivalBeforeDeparture_returns400() throws Exception {
        CreateFlightRequest req = new CreateFlightRequest(
            "TK1982", "TK", "TC-JFC",
            "LTBA", "LTFM", FlightType.PASSENGER,
            arrival(),     // departure olarak daha geç zaman
            departure()    // arrival olarak daha erken zaman
        );
        mockMvc.perform(post("/api/flights")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "officer1", roles = "OPERATION_OFFICER")
    void createFlight_pastDate_returns400() throws Exception {
        CreateFlightRequest req = new CreateFlightRequest(
            "TK1983", "TK", "TC-JFC",
            "LTBA", "LTFM", FlightType.PASSENGER,
            LocalDateTime.of(2020, 1, 1, 10, 0),
            LocalDateTime.of(2020, 1, 1, 12, 0)
        );
        mockMvc.perform(post("/api/flights")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest());
    }

    // ─── Yetki ────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "analyst1", roles = "BI_SPECIALIST")
    void createFlight_biSpecialist_returns403() throws Exception {
        mockMvc.perform(post("/api/flights")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson()))
            .andExpect(status().isForbidden());
    }

    // ─── CSV Toplu Yükleme ─────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "officer1", roles = "OPERATION_OFFICER")
    void bulkUpload_3valid2invalid_returns3Saved2Errors() throws Exception {
        // Dinamik tarihler — @Future validasyonunu kalıcı olarak geçer
        LocalDateTime dep1 = LocalDateTime.now().plusDays(10).withHour(10).withMinute(30).withSecond(0).withNano(0);
        LocalDateTime arr1 = dep1.withHour(12).withMinute(0);
        LocalDateTime dep2 = dep1.plusDays(1);   LocalDateTime arr2 = arr1.plusDays(1);
        LocalDateTime dep3 = dep1.plusDays(2);   LocalDateTime arr3 = arr1.plusDays(2);
        LocalDateTime dep4 = dep1.plusDays(3);   LocalDateTime arr4 = arr1.plusDays(3);
        LocalDateTime dep5 = dep1.plusDays(4);   LocalDateTime arr5 = arr1.plusDays(4);

        String csv = "flightNumber,airlineCode,aircraftTailNumber,originStation,destinationStation,flightType,scheduledDeparture,scheduledArrival\n"
            + "TK1990,TK,TC-JFC,LTBA,LTFM,PASSENGER," + dep1.format(FMT) + "," + arr1.format(FMT) + "\n"
            + "TK1991,TK,TC-JFC,LTBA,LTFM,CARGO,"     + dep2.format(FMT) + "," + arr2.format(FMT) + "\n"
            + "TK1992,TK,TC-JFC,LTBA,LTFM,POSITION,"  + dep3.format(FMT) + "," + arr3.format(FMT) + "\n"
            + "INVALID,TK,TC-JFC,LTBA,LTFM,CARGO,"    + dep4.format(FMT) + "," + arr4.format(FMT) + "\n"  // format hatası
            + "TK1993,TK,TC-JFC,LTBA,LTBA,PASSENGER," + dep5.format(FMT) + "," + arr5.format(FMT) + "\n"; // origin=dest

        MockMultipartFile file = new MockMultipartFile(
            "file", "flights.csv", "text/csv", csv.getBytes());

        String json = mockMvc.perform(multipart("/api/flights/bulk").file(file))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        BulkUploadResult result = objectMapper.readValue(json, BulkUploadResult.class);
        assertThat(result.totalRows()).isEqualTo(5);        // totalRows = successCount + failureCount
        assertThat(result.successCount()).isEqualTo(3);
        assertThat(result.failureCount()).isEqualTo(2);
        assertThat(result.errors()).hasSize(2);
    }

    @Test
    @WithMockUser(username = "officer1", roles = "OPERATION_OFFICER")
    void bulkUpload_duplicateRow_goesToErrors() throws Exception {
        // Önce tek kayıt ekle
        mockMvc.perform(post("/api/flights")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson()))
            .andExpect(status().isCreated());

        // CSV'de aynı uçuş tekrar
        String csv = "flightNumber,airlineCode,aircraftTailNumber,originStation,destinationStation,flightType,scheduledDeparture,scheduledArrival\n"
            + "TK1980,TK,TC-JFC,LTBA,LTFM,PASSENGER," + departure().format(FMT) + "," + arrival().format(FMT) + "\n";

        MockMultipartFile file = new MockMultipartFile(
            "file", "dup.csv", "text/csv", csv.getBytes());

        String json = mockMvc.perform(multipart("/api/flights/bulk").file(file))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        BulkUploadResult result = objectMapper.readValue(json, BulkUploadResult.class);
        assertThat(result.successCount()).isZero();
        assertThat(result.errors()).hasSize(1);
    }

    // ─── Rate Limiting ─────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "rateuser", roles = "OPERATION_OFFICER")
    void rateLimiting_31stRequest_returns429() throws Exception {
        for (int i = 1; i <= 30; i++) {
            LocalDateTime dep = LocalDateTime.now().plusDays(30 + i).withHour(10).withMinute(0).withSecond(0).withNano(0);
            LocalDateTime arr = dep.plusHours(2);
            CreateFlightRequest req = new CreateFlightRequest(
                "TK" + String.format("%04d", i), "TK", "TC-JFC",
                "LTBA", "LTFM", FlightType.PASSENGER,
                dep, arr
            );
            mockMvc.perform(post("/api/flights")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)));
        }

        // 31. istek → 429
        mockMvc.perform(post("/api/flights")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson()))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.error").value("Dakikalık istek limiti aşıldı (maks 30)"));
    }
}
