package com.tav.FlightService.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tav.FlightService.common.exception.GlobalExceptionHandler;
import com.tav.FlightService.dto.BulkUploadResult;
import com.tav.FlightService.dto.CreateFlightRequest;
import com.tav.FlightService.dto.FlightResponse;
import com.tav.FlightService.dto.UpdateFlightRequest;
import com.tav.FlightService.exception.NotFoundException;
import com.tav.FlightService.service.FlightCsvService;
import com.tav.FlightService.service.FlightService;
import com.tav.FlightService.domain.FlightType;
import com.tav.FlightService.util.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@WebMvcTest(controllers = FlightController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class FlightControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean FlightService flightService;
    @MockBean FlightCsvService flightCsvService;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private FlightResponse response;

    @BeforeEach
    void setUp() {
        response = TestDataFactory.buildFlightResponse();
    }

    // ------------------------------------------------------------------ LIST / GET

    @Test
    @DisplayName("GET /api/flights → 200 ile uçuş listesi döner")
    void listFlights_returns200WithArray() throws Exception {
        // given
        when(flightService.findAll()).thenReturn(List.of(response));

        // when / then
        mockMvc.perform(get("/api/flights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].flightNumber").value(response.flightNumber()))
                .andExpect(jsonPath("$[0].airlineCode").value(response.airlineCode()));
        verify(flightService).findAll();
    }

    @Test
    @DisplayName("GET /api/flights?flightDate=YYYY-MM-DD → tarihe göre filtrelenir")
    void listFlights_withDate_filtersByDate() throws Exception {
        // given
        when(flightService.findByFlightDate(any())).thenReturn(List.of(response));

        // when / then
        mockMvc.perform(get("/api/flights").param("flightDate", "2026-06-23"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        verify(flightService).findByFlightDate(eq(java.time.LocalDate.of(2026, 6, 23)));
    }

    @Test
    @DisplayName("GET /api/flights/{id} → mevcut uçuş 200 döner")
    void getFlight_existing_returns200() throws Exception {
        // given
        when(flightService.findById(1L)).thenReturn(response);

        // when / then
        mockMvc.perform(get("/api/flights/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("GET /api/flights/{id} → bulunamadığında 404")
    void getFlight_notFound_returns404() throws Exception {
        // given
        when(flightService.findById(999L)).thenThrow(new NotFoundException("Uçuş bulunamadı"));

        // when / then
        mockMvc.perform(get("/api/flights/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/flights/sorted → 200 döner")
    void getSortedByDeparture_returns200() throws Exception {
        // given
        when(flightService.getFlightsSortedByDeparture(50)).thenReturn(List.of(response));

        // when / then
        mockMvc.perform(get("/api/flights/sorted"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    // ------------------------------------------------------------------ CREATE

    @Test
    @DisplayName("POST /api/flights → geçerli payload 201 döner")
    void createFlight_valid_returns201() throws Exception {
        // given
        CreateFlightRequest request = futureCreateRequest();
        when(flightService.create(any())).thenReturn(response);

        // when / then
        mockMvc.perform(post("/api/flights")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.flightNumber").value(response.flightNumber()));
    }

    @Test
    @DisplayName("POST /api/flights → geçersiz flightNumber 400 döner")
    void createFlight_invalidFlightNumber_returns400() throws Exception {
        // given
        CreateFlightRequest request = new CreateFlightRequest(
                "INVALID", "TK", "TC-JFA",
                "LTFM", "LTAC", FlightType.PASSENGER,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2));

        // when / then
        mockMvc.perform(post("/api/flights")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.flightNumber").exists());
    }

    @Test
    @DisplayName("POST /api/flights → geçersiz ICAO 400 döner")
    void createFlight_invalidIcao_returns400() throws Exception {
        // given
        CreateFlightRequest request = new CreateFlightRequest(
                "TK0001", "TK", "TC-JFA",
                "ist", "LTAC", FlightType.PASSENGER,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2));

        // when / then
        mockMvc.perform(post("/api/flights")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.originStation").exists());
    }

    @Test
    @DisplayName("POST /api/flights → blank airlineCode 400 döner")
    void createFlight_blankAirline_returns400() throws Exception {
        // given
        CreateFlightRequest request = new CreateFlightRequest(
                "TK0001", "", "TC-JFA",
                "LTFM", "LTAC", FlightType.PASSENGER,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2));

        // when / then
        mockMvc.perform(post("/api/flights")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ UPDATE / DELETE

    @Test
    @DisplayName("PUT /api/flights/{id} → 200 döner")
    void updateFlight_existing_returns200() throws Exception {
        // given
        UpdateFlightRequest request = new UpdateFlightRequest(
                LocalDateTime.now().plusDays(2),
                LocalDateTime.now().plusDays(2).plusHours(2),
                FlightType.PASSENGER);
        when(flightService.update(eq(1L), any())).thenReturn(response);

        // when / then
        mockMvc.perform(put("/api/flights/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("DELETE /api/flights/{id} → 204 döner")
    void deleteFlight_existing_returns204() throws Exception {
        // when / then
        mockMvc.perform(delete("/api/flights/1"))
                .andExpect(status().isNoContent());
        verify(flightService).delete(1L);
    }

    // ------------------------------------------------------------------ BULK CSV

    @Test
    @DisplayName("POST /api/flights/bulk → CSV upload sonuç döner")
    void uploadCsv_validFile_returns200WithReport() throws Exception {
        // given
        when(flightCsvService.upload(any())).thenReturn(new BulkUploadResult(2, 2, 0, List.of()));
        MockMultipartFile file = new MockMultipartFile(
                "file", "flights.csv", MediaType.TEXT_PLAIN_VALUE,
                "header\nrow".getBytes());

        // when / then
        mockMvc.perform(multipart("/api/flights/bulk").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(2))
                .andExpect(jsonPath("$.successCount").value(2))
                .andExpect(jsonPath("$.failureCount").value(0));
    }

    private CreateFlightRequest futureCreateRequest() {
        LocalDateTime dep = LocalDateTime.now().plusDays(1);
        return new CreateFlightRequest(
                "TK0001", "TK", "TC-JFA",
                "LTFM", "LTAC", FlightType.PASSENGER,
                dep, dep.plusHours(2));
    }
}
