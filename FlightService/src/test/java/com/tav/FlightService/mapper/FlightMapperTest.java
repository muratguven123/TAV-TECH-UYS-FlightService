package com.tav.FlightService.mapper;

import com.tav.FlightService.domain.Flight;
import com.tav.FlightService.domain.FlightType;
import com.tav.FlightService.dto.CreateFlightRequest;
import com.tav.FlightService.dto.FlightResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class FlightMapperTest {

    private final FlightMapper mapper = Mappers.getMapper(FlightMapper.class);

    @Test
    @DisplayName("toEntity → tüm DTO alanları kopyalanır, flightDate scheduledDeparture'dan türetilir")
    void toEntity_mapsAllFields() {
        // given
        LocalDateTime dep = LocalDateTime.of(2026, 6, 23, 10, 0);
        LocalDateTime arr = LocalDateTime.of(2026, 6, 23, 11, 30);
        CreateFlightRequest req = new CreateFlightRequest(
                "TK0001", "TK", "TC-JFA",
                "LTFM", "LTAC", FlightType.PASSENGER,
                dep, arr);

        // when
        Flight entity = mapper.toEntity(req);

        // then
        assertThat(entity).isNotNull();
        assertThat(entity.getFlightNumber()).isEqualTo("TK0001");
        assertThat(entity.getAirlineCode()).isEqualTo("TK");
        assertThat(entity.getAircraftTailNumber()).isEqualTo("TC-JFA");
        assertThat(entity.getOriginStation()).isEqualTo("LTFM");
        assertThat(entity.getDestinationStation()).isEqualTo("LTAC");
        assertThat(entity.getFlightType()).isEqualTo(FlightType.PASSENGER);
        assertThat(entity.getScheduledDeparture()).isEqualTo(dep);
        assertThat(entity.getScheduledArrival()).isEqualTo(arr);
        assertThat(entity.getFlightDate()).isEqualTo(LocalDate.of(2026, 6, 23));
    }

    @Test
    @DisplayName("toEntity → ID, version, audit alanları ignore edilir")
    void toEntity_doesNotSetIdVersionOrAuditFields() {
        // given
        CreateFlightRequest req = new CreateFlightRequest(
                "TK0001", "TK", "TC-JFA",
                "LTFM", "LTAC", FlightType.PASSENGER,
                LocalDateTime.of(2026, 6, 23, 10, 0),
                LocalDateTime.of(2026, 6, 23, 11, 30));

        // when
        Flight entity = mapper.toEntity(req);

        // then
        assertThat(entity.getId()).isNull();
        assertThat(entity.getVersion()).isNull();
        assertThat(entity.getCreatedAt()).isNull();
        assertThat(entity.getUpdatedAt()).isNull();
    }

    @Test
    @DisplayName("toEntity → null girdi null döner")
    void toEntity_null_returnsNull() {
        assertThat(mapper.toEntity(null)).isNull();
    }

    @Test
    @DisplayName("toResponse → tüm entity alanları FlightResponse'a kopyalanır")
    void toResponse_mapsAllFields() {
        // given
        Flight entity = new Flight();
        entity.setId(42L);
        entity.setFlightNumber("TK0001");
        entity.setAirlineCode("TK");
        entity.setAircraftTailNumber("TC-JFA");
        entity.setOriginStation("LTFM");
        entity.setDestinationStation("LTAC");
        entity.setFlightType(FlightType.PASSENGER);
        entity.setScheduledDeparture(LocalDateTime.of(2026, 6, 23, 10, 0));
        entity.setScheduledArrival(LocalDateTime.of(2026, 6, 23, 11, 30));
        entity.setFlightDate(LocalDate.of(2026, 6, 23));
        entity.setVersion(7L);

        // when
        FlightResponse response = mapper.toResponse(entity);

        // then
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(42L);
        assertThat(response.flightNumber()).isEqualTo("TK0001");
        assertThat(response.airlineCode()).isEqualTo("TK");
        assertThat(response.aircraftTailNumber()).isEqualTo("TC-JFA");
        assertThat(response.originStation()).isEqualTo("LTFM");
        assertThat(response.destinationStation()).isEqualTo("LTAC");
        assertThat(response.flightType()).isEqualTo(FlightType.PASSENGER);
        assertThat(response.scheduledDeparture()).isEqualTo(LocalDateTime.of(2026, 6, 23, 10, 0));
        assertThat(response.scheduledArrival()).isEqualTo(LocalDateTime.of(2026, 6, 23, 11, 30));
        assertThat(response.flightDate()).isEqualTo(LocalDate.of(2026, 6, 23));
        assertThat(response.version()).isEqualTo(7L);
    }

    @Test
    @DisplayName("toResponse → null entity null response döner")
    void toResponse_null_returnsNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }
}
