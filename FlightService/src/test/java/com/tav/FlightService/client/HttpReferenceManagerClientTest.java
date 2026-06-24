package com.tav.FlightService.client;

import com.tav.FlightService.client.dto.AircraftDto;
import com.tav.FlightService.client.dto.AirlineDto;
import com.tav.FlightService.client.dto.RouteDto;
import com.tav.FlightService.client.dto.StationDto;
import com.tav.FlightService.exception.ServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HttpReferenceManagerClientTest {

    @Mock RestClient restClient;
    @Mock RestClient.RequestHeadersUriSpec<?> uriSpec;
    @Mock RestClient.RequestHeadersSpec<?> headersSpec;
    @Mock RestClient.ResponseSpec responseSpec;

    HttpReferenceManagerClient client;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() {
        client = new HttpReferenceManagerClient(restClient);
        lenient().when(restClient.get()).thenReturn((RestClient.RequestHeadersUriSpec) uriSpec);
        lenient().when(uriSpec.uri(any(String.class), any(Object[].class)))
                .thenReturn((RestClient.RequestHeadersSpec) headersSpec);
        lenient().when(headersSpec.retrieve()).thenReturn(responseSpec);
    }

    // ------------------------------------------------------------------ AIRLINE

    @Test
    @DisplayName("getAirline → 200 ile dolu Optional döner")
    void getAirline_200_returnsAirlineDto() {
        // given
        AirlineDto dto = new AirlineDto(1L, "Turkish Airlines", "TK");
        when(responseSpec.body(AirlineDto.class)).thenReturn(dto);

        // when
        Optional<AirlineDto> result = client.getAirline("TK");

        // then
        assertThat(result).contains(dto);
    }

    @Test
    @DisplayName("getAirline → 404 NotFound → Optional.empty()")
    void getAirline_404_returnsEmpty() {
        // given
        when(responseSpec.body(AirlineDto.class))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.NOT_FOUND, "Not Found", null, null, null));

        // when
        Optional<AirlineDto> result = client.getAirline("XX");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getAirline → 401 → ServiceUnavailableException")
    void getAirline_401_throwsServiceUnavailable() {
        // given
        when(responseSpec.body(AirlineDto.class))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.UNAUTHORIZED, "Unauthorized", null, null, null));

        // when / then
        assertThatThrownBy(() -> client.getAirline("TK"))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("getAirline → 400 (diğer 4xx) → ServiceUnavailableException")
    void getAirline_otherClientError_throwsServiceUnavailable() {
        // given
        when(responseSpec.body(AirlineDto.class))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.BAD_REQUEST, "Bad Request", null, null, null));

        // when / then
        assertThatThrownBy(() -> client.getAirline("TK"))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("getAirline → IOException / ResourceAccessException → ServiceUnavailableException")
    void getAirline_networkError_throwsServiceUnavailable() {
        // given
        when(responseSpec.body(AirlineDto.class))
                .thenThrow(new ResourceAccessException("connect timed out"));

        // when / then
        assertThatThrownBy(() -> client.getAirline("TK"))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("erişilemez");
    }

    @Test
    @DisplayName("getAirline → null body → Optional.empty()")
    void getAirline_nullBody_returnsEmpty() {
        // given
        when(responseSpec.body(AirlineDto.class)).thenReturn(null);

        // when
        Optional<AirlineDto> result = client.getAirline("TK");

        // then
        assertThat(result).isEmpty();
    }

    // ------------------------------------------------------------------ AIRCRAFT

    @Test
    @DisplayName("getAircraft → 200 ile dolu Optional döner")
    void getAircraft_200_returnsDto() {
        // given
        AircraftDto dto = new AircraftDto(1L, "A320", "TC-JFA");
        when(responseSpec.body(AircraftDto.class)).thenReturn(dto);

        // when
        Optional<AircraftDto> result = client.getAircraft("TC-JFA");

        // then
        assertThat(result).contains(dto);
    }

    @Test
    @DisplayName("getAircraft → 404 → Optional.empty()")
    void getAircraft_404_returnsEmpty() {
        // given
        when(responseSpec.body(AircraftDto.class))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.NOT_FOUND, "Not Found", null, null, null));

        // when
        Optional<AircraftDto> result = client.getAircraft("UNK");

        // then
        assertThat(result).isEmpty();
    }

    // ------------------------------------------------------------------ STATION

    @Test
    @DisplayName("getStation → 200 ile dolu Optional döner")
    void getStation_200_returnsDto() {
        // given
        StationDto dto = new StationDto(1L, "LTFM", "Istanbul Airport");
        when(responseSpec.body(StationDto.class)).thenReturn(dto);

        // when
        Optional<StationDto> result = client.getStation("LTFM");

        // then
        assertThat(result).contains(dto);
    }

    @Test
    @DisplayName("getStation → 5xx → ServiceUnavailableException")
    void getStation_5xx_throwsServiceUnavailable() {
        // given — RestClient retrieve() 5xx'i ayrı çevirir; burada sahte 4xx kullanıyoruz
        // ama generic catch RestClientException kapsar.
        when(responseSpec.body(StationDto.class))
                .thenThrow(new org.springframework.web.client.RestClientException("server error"));

        // when / then
        assertThatThrownBy(() -> client.getStation("LTFM"))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    // ------------------------------------------------------------------ ROUTE

    @Test
    @DisplayName("getRoute → 200 ile dolu Optional döner")
    void getRoute_200_returnsDto() {
        // given
        RouteDto dto = new RouteDto(1L,
                new StationDto(1L, "LTFM", "Istanbul"),
                new StationDto(2L, "LTAC", "Ankara"));
        when(responseSpec.body(RouteDto.class)).thenReturn(dto);

        // when
        Optional<RouteDto> result = client.getRoute("LTFM", "LTAC");

        // then
        assertThat(result).contains(dto);
    }

    @Test
    @DisplayName("getRoute → 404 → Optional.empty()")
    void getRoute_404_returnsEmpty() {
        // given
        when(responseSpec.body(RouteDto.class))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.NOT_FOUND, "Not Found", null, null, null));

        // when
        Optional<RouteDto> result = client.getRoute("LTFM", "LTAC");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getRoute → 403 Forbidden → ServiceUnavailableException")
    void getRoute_403_throwsServiceUnavailable() {
        // given
        when(responseSpec.body(RouteDto.class))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.FORBIDDEN, "Forbidden", null, null, null));

        // when / then
        assertThatThrownBy(() -> client.getRoute("LTFM", "LTAC"))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("kimlik doğrulama");
    }
}
