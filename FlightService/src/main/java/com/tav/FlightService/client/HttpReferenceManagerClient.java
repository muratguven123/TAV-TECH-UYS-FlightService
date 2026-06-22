package com.tav.FlightService.client;

import com.tav.FlightService.client.dto.AircraftDto;
import com.tav.FlightService.client.dto.AirlineDto;
import com.tav.FlightService.client.dto.RouteDto;
import com.tav.FlightService.client.dto.StationDto;
import com.tav.FlightService.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class HttpReferenceManagerClient implements ReferenceManagerClient {

    private final RestClient referenceManagerRestClient;

    @Override
    public Optional<AirlineDto> getAirline(String iataCode) {
        return get("/api/reference/airlines/by-code/{code}", AirlineDto.class, iataCode);
    }

    @Override
    public Optional<AircraftDto> getAircraft(String tailNumber) {
        return get("/api/reference/aircrafts/by-tail/{tail}", AircraftDto.class, tailNumber);
    }

    @Override
    public Optional<StationDto> getStation(String icao) {
        return get("/api/reference/stations/by-code/{icao}", StationDto.class, icao);
    }

    @Override
    public Optional<RouteDto> getRoute(String originIcao, String destinationIcao) {
        try {
            RouteDto dto = referenceManagerRestClient.get()
                    .uri("/api/reference/routes/by-codes?origin={o}&destination={d}", originIcao, destinationIcao)
                    .retrieve()
                    .body(RouteDto.class);
            return Optional.ofNullable(dto);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            log.error("Reference Manager auth hatası (route {}-{}): status={}", originIcao, destinationIcao, e.getStatusCode());
            throw new ServiceUnavailableException("Reference Manager kimlik doğrulama hatası: " + e.getStatusCode());
        } catch (HttpClientErrorException e) {
            log.error("Reference Manager istemci hatası (route {}-{}): status={}", originIcao, destinationIcao, e.getStatusCode());
            throw new ServiceUnavailableException("Reference Manager hata döndürdü: " + e.getStatusCode());
        } catch (RestClientException e) {
            log.error("Reference Manager erişilemez (route {}-{}): {}", originIcao, destinationIcao, e.getMessage());
            throw new ServiceUnavailableException("Reference Manager erişilemez: " + e.getMessage());
        }
    }

    private <T> Optional<T> get(String uriTemplate, Class<T> responseType, Object... uriVars) {
        try {
            T body = referenceManagerRestClient.get()
                    .uri(uriTemplate, uriVars)
                    .retrieve()
                    .body(responseType);
            return Optional.ofNullable(body);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            log.error("Reference Manager auth hatası ({}): status={}", uriTemplate, e.getStatusCode());
            throw new ServiceUnavailableException("Reference Manager kimlik doğrulama hatası: " + e.getStatusCode());
        } catch (HttpClientErrorException e) {
            log.error("Reference Manager istemci hatası ({}): status={}", uriTemplate, e.getStatusCode());
            throw new ServiceUnavailableException("Reference Manager hata döndürdü: " + e.getStatusCode());
        } catch (RestClientException e) {
            log.error("Reference Manager erişilemez ({}): {}", uriTemplate, e.getMessage());
            throw new ServiceUnavailableException("Reference Manager erişilemez: " + e.getMessage());
        }
    }
}
