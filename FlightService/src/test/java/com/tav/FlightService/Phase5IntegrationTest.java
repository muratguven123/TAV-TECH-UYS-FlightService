package com.tav.FlightService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tav.FlightService.client.ReferenceManagerClient;
import com.tav.FlightService.client.dto.AircraftDto;
import com.tav.FlightService.client.dto.AirlineDto;
import com.tav.FlightService.client.dto.RouteDto;
import com.tav.FlightService.client.dto.StationDto;
import com.tav.FlightService.domain.FlightType;
import com.tav.FlightService.dto.CreateFlightRequest;
import com.tav.FlightService.dto.UpdateFlightRequest;
import com.tav.FlightService.exception.ServiceUnavailableException;
import com.tav.FlightService.repository.FlightRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1, topics = {"reference.events"})
@TestPropertySource(properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:h2:mem:phase5db;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
        "app.jwt.secret=test-secret-key-minimum-32-bytes-ok!",
        "app.jwt.expiration-ms=3600000",
        "app.reference.base-url=http://localhost:9999",
        "app.reference.validator=caching",
        "app.internal-user.username=flight-service-internal",
        "app.gateway.auth.enabled=false"
})
class Phase5IntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired FlightRepository flightRepository;
    @Autowired TransactionTemplate transactionTemplate;
    @PersistenceContext EntityManager entityManager;

    @MockBean StringRedisTemplate stringRedisTemplate;
    @MockBean ReferenceManagerClient referenceManagerClient;

    @SuppressWarnings("unchecked")
    @MockBean(name = "valueOps")
    ValueOperations<String, String> valueOps;

    private static final AirlineDto  AIRLINE = new AirlineDto(1L, "Turkish Airlines", "TK");
    private static final AircraftDto AIRCRAFT = new AircraftDto(1L, "B738", "TC-JFC");
    private static final StationDto  LTBA = new StationDto(1L, "LTBA", "Istanbul Ataturk");
    private static final StationDto  LTFM = new StationDto(2L, "LTFM", "Istanbul Airport");
    private static final RouteDto    ROUTE = new RouteDto(1L, LTBA, LTFM);

    @BeforeEach
    void setUp() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        doNothing().when(valueOps).set(anyString(), anyString(), any());

        when(referenceManagerClient.getAirline("TK")).thenReturn(Optional.of(AIRLINE));
        when(referenceManagerClient.getAircraft("TC-JFC")).thenReturn(Optional.of(AIRCRAFT));
        when(referenceManagerClient.getStation("LTBA")).thenReturn(Optional.of(LTBA));
        when(referenceManagerClient.getStation("LTFM")).thenReturn(Optional.of(LTFM));
        when(referenceManagerClient.getRoute("LTBA", "LTFM")).thenReturn(Optional.of(ROUTE));
    }

    // ─── Yardımcı ──────────────────────────────────────────────────────────────

    private LocalDateTime departure() {
        return LocalDateTime.now().plusDays(30).withHour(10).withMinute(30).withSecond(0).withNano(0);
    }

    private LocalDateTime arrival() {
        return LocalDateTime.now().plusDays(30).withHour(12).withMinute(0).withSecond(0).withNano(0);
    }

    /**
     * D3: @WithMockUser helper metodlara uygulanmaz — her MockMvc çağrısında
     * .with(user(...)) kullanılır.
     */
    private Long createFlight(String flightNum) throws Exception {
        // Cache miss: Redis get() → null
        when(valueOps.get(anyString())).thenReturn(null);

        String resp = mockMvc.perform(post("/api/flights")
                        .with(user("test-officer").roles("OPERATION_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateFlightRequest(
                                flightNum, "TK", "TC-JFC", "LTBA", "LTFM",
                                FlightType.PASSENGER, departure(), arrival()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(resp).get("id").asLong();
    }

    // ─── UC-03 Cache hit: Redis'te var → RM çağrısı YOK ──────────────────────

    @Test
    void cacheHit_rmNotCalled() throws Exception {
        // Tüm key'ler için Redis "1" döner
        when(valueOps.get(anyString())).thenReturn("1");

        mockMvc.perform(post("/api/flights")
                        .with(user("officer").roles("OPERATION_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateFlightRequest(
                                "TK2001", "TK", "TC-JFC", "LTBA", "LTFM",
                                FlightType.PASSENGER, departure(), arrival()))))
                .andExpect(status().isCreated());

        verifyNoInteractions(referenceManagerClient);
    }

    // ─── UC-03 Negatif cache hit: Redis "0" → RM çağrısı YOK, 422 dönmeli ───

    @Test
    void negativeCacheHit_rejectsWithoutCallingRm() throws Exception {
        when(valueOps.get(anyString())).thenReturn("1");  // diğerleri pozitif
        when(valueOps.get("reference:AIRLINE:TK")).thenReturn("0");

        mockMvc.perform(post("/api/flights")
                        .with(user("officer").roles("OPERATION_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateFlightRequest(
                                "TK2009", "TK", "TC-JFC", "LTBA", "LTFM",
                                FlightType.PASSENGER, departure(), arrival()))))
                .andExpect(status().isUnprocessableEntity());

        verify(referenceManagerClient, never()).getAirline(anyString());
    }

    // ─── UC-04 Cache miss: RM çağrılır, Redis dolar ───────────────────────────

    @Test
    void cacheMiss_rmCalledAndCacheWarmed() throws Exception {
        // Tüm key'ler için miss (null döner)
        when(valueOps.get(anyString())).thenReturn(null);

        mockMvc.perform(post("/api/flights")
                        .with(user("officer").roles("OPERATION_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateFlightRequest(
                                "TK2002", "TK", "TC-JFC", "LTBA", "LTFM",
                                FlightType.PASSENGER, departure(), arrival()))))
                .andExpect(status().isCreated());

        verify(referenceManagerClient).getAirline("TK");
        verify(referenceManagerClient).getAircraft("TC-JFC");
        verify(referenceManagerClient, times(2)).getStation(anyString());
        verify(referenceManagerClient).getRoute("LTBA", "LTFM");

        // D6: airline + aircraft + LTBA + LTFM + route = 5 pozitif cache yazımı
        verify(valueOps, times(5)).set(anyString(), eq("1"), any());
    }

    // ─── UC-04 Cache miss 404: RM bulunamadı, negatif cache yazılır ──────────

    @Test
    void cacheMiss_rmReturns404_writesNegativeCache() throws Exception {
        when(valueOps.get(anyString())).thenReturn(null);
        when(referenceManagerClient.getAirline("TK")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/flights")
                        .with(user("officer").roles("OPERATION_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateFlightRequest(
                                "TK2003", "TK", "TC-JFC", "LTBA", "LTFM",
                                FlightType.PASSENGER, departure(), arrival()))))
                .andExpect(status().isUnprocessableEntity());

        // Negatif cache: AIRLINE:TK için "0" yazılmış olmalı
        verify(valueOps).set(eq("reference:AIRLINE:TK"), eq("0"), any());
    }

    // ─── Redis çökerse fail-open: RM'e düşer ─────────────────────────────────

    @Test
    void redisDown_failOpen_rmCalled() throws Exception {
        when(valueOps.get(anyString()))
                .thenThrow(new DataAccessException("Redis down") {});

        mockMvc.perform(post("/api/flights")
                        .with(user("officer").roles("OPERATION_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateFlightRequest(
                                "TK2004", "TK", "TC-JFC", "LTBA", "LTFM",
                                FlightType.PASSENGER, departure(), arrival()))))
                .andExpect(status().isCreated());

        verify(referenceManagerClient).getAirline("TK");
    }

    // ─── RM erişilemez → 503 ─────────────────────────────────────────────────

    @Test
    void rmDown_returns503() throws Exception {
        when(valueOps.get(anyString())).thenReturn(null);
        when(referenceManagerClient.getAirline("TK"))
                .thenThrow(new ServiceUnavailableException("Reference Manager erişilemez"));

        mockMvc.perform(post("/api/flights")
                        .with(user("officer").roles("OPERATION_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateFlightRequest(
                                "TK2005", "TK", "TC-JFC", "LTBA", "LTFM",
                                FlightType.PASSENGER, departure(), arrival()))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }

    // ─── Update happy path: version 0 → 1 ────────────────────────────────────

    @Test
    void update_happyPath_versionIncremented() throws Exception {
        Long id = createFlight("TK2010");
        reset(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);

        UpdateFlightRequest upd = new UpdateFlightRequest(
                departure().plusHours(1), arrival().plusHours(2), FlightType.CARGO);

        mockMvc.perform(put("/api/flights/" + id)
                        .with(user("officer").roles("OPERATION_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(upd)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flightType").value("CARGO"))
                .andExpect(jsonPath("$.version").value(1));
    }

    // ─── Update 404 ───────────────────────────────────────────────────────────

    @Test
    void update_notFound_returns409() throws Exception {
        UpdateFlightRequest upd = new UpdateFlightRequest(
                departure(), arrival(), FlightType.PASSENGER);

        mockMvc.perform(put("/api/flights/999999")
                        .with(user("officer").roles("OPERATION_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(upd)))
                .andExpect(status().isConflict());
    }

    // ─── D5: Optimistic lock → 409 STALE_VERSION — EntityManager ile ─────────

    @Test
    @org.springframework.transaction.annotation.Transactional
    void optimisticLock_staleVersion_returns409() throws Exception {
        Long id = createFlight("TK2020");
        reset(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);

        // İlk update: başarılı → version 1
        UpdateFlightRequest req1 = new UpdateFlightRequest(
                departure().plusHours(1), arrival().plusHours(1), FlightType.CARGO);
        mockMvc.perform(put("/api/flights/" + id)
                        .with(user("officer").roles("OPERATION_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        // DB'de version'ı eski değere (0) dön — gerçek çakışma simülasyonu
        transactionTemplate.execute(status -> {
            entityManager.createNativeQuery(
                            "UPDATE flights SET version = 0 WHERE id = :id")
                    .setParameter("id", id)
                    .executeUpdate();
            return null;
        });

        // İkinci update: Hibernate version=1 bekliyor, DB'de 0 var → çakışma
        UpdateFlightRequest req2 = new UpdateFlightRequest(
                departure().plusHours(2), arrival().plusHours(3), FlightType.PASSENGER);
        mockMvc.perform(put("/api/flights/" + id)
                        .with(user("officer").roles("OPERATION_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"));
    }

    // ─── Delete → 204 ────────────────────────────────────────────────────────

    @Test
    void delete_happyPath_returns204() throws Exception {
        Long id = createFlight("TK2030");

        mockMvc.perform(delete("/api/flights/" + id)
                        .with(user("officer").roles("OPERATION_OFFICER")))
                .andExpect(status().isNoContent());

        assertThat(flightRepository.findById(id)).isEmpty();
    }

    // ─── Delete 404 ───────────────────────────────────────────────────────────

    @Test
    void delete_notFound_returns409() throws Exception {
        mockMvc.perform(delete("/api/flights/999999")
                        .with(user("officer").roles("OPERATION_OFFICER")))
                .andExpect(status().isConflict());
    }

    // ─── BI_SPECIALIST PUT/DELETE → 403 ──────────────────────────────────────

    @Test
    void biSpecialist_put_returns403() throws Exception {
        UpdateFlightRequest upd = new UpdateFlightRequest(
                departure(), arrival(), FlightType.PASSENGER);

        mockMvc.perform(put("/api/flights/1")
                        .with(user("analyst").roles("BI_SPECIALIST"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(upd)))
                .andExpect(status().isForbidden());
    }

    @Test
    void biSpecialist_delete_returns403() throws Exception {
        mockMvc.perform(delete("/api/flights/1")
                        .with(user("analyst").roles("BI_SPECIALIST")))
                .andExpect(status().isForbidden());
    }
}
