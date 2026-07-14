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

    // FIX: DEF-009 — getRoute artık ortak get() helper'ını kullanır; query params path placeholder olarak geçilir.
    // Spring RestClient uri(template, vars...) query string placeholder'larını da destekler.
    @Override
    public Optional<RouteDto> getRoute(String originIcao, String destinationIcao) {
        return get("/api/reference/routes/by-codes?origin={o}&destination={d}",
                RouteDto.class, originIcao, destinationIcao);
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
