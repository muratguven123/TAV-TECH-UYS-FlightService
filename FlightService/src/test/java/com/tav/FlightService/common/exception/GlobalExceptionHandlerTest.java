package com.tav.FlightService.common.exception;

import com.tav.FlightService.exception.FlightBusinessException;
import com.tav.FlightService.exception.FlightConflictException;
import com.tav.FlightService.exception.NotFoundException;
import com.tav.FlightService.exception.ReferenceNotFoundException;
import com.tav.FlightService.exception.ServiceUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("NotFoundException → 404 + error mesajı")
    void handleNotFound_returns404() {
        // when
        ResponseEntity<Map<String, Object>> response =
                handler.handleNotFound(new NotFoundException("Uçuş bulunamadı"));

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("status", 404);
        assertThat(response.getBody()).containsEntry("error", "Uçuş bulunamadı");
        assertThat(response.getBody()).containsKey("timestamp");
    }

    @Test
    @DisplayName("BusinessException → 409 CONFLICT")
    void handleBusiness_returns409() {
        // when
        ResponseEntity<Map<String, Object>> response =
                handler.handleBusiness(new BusinessException("İş kuralı ihlali"));

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("error", "İş kuralı ihlali");
    }

    @Test
    @DisplayName("FlightConflictException → 409 CONFLICT")
    void handleFlightConflict_returns409() {
        // when
        ResponseEntity<Map<String, Object>> response =
                handler.handleFlightConflict(new FlightConflictException("Çakışma"));

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("error", "Çakışma");
    }

    @Test
    @DisplayName("FlightBusinessException → 400 BAD_REQUEST")
    void handleFlightBusiness_returns400() {
        // when
        ResponseEntity<Map<String, Object>> response =
                handler.handleFlightBusiness(new FlightBusinessException("Geçersiz uçuş"));

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "Geçersiz uçuş");
    }

    @Test
    @DisplayName("ReferenceNotFoundException → 422 UNPROCESSABLE_ENTITY")
    void handleReferenceNotFound_returns422() {
        // when
        ResponseEntity<Map<String, Object>> response =
                handler.handleReferenceNotFound(new ReferenceNotFoundException("Havayolu bulunamadı"));

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).containsEntry("status", 422);
    }

    @Test
    @DisplayName("ServiceUnavailableException → 503 + SERVICE_UNAVAILABLE code")
    void handleServiceUnavailable_returns503() {
        // when
        ResponseEntity<Map<String, Object>> response =
                handler.handleServiceUnavailable(new ServiceUnavailableException("Reference Manager erişilemez"));

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("code", "SERVICE_UNAVAILABLE");
        assertThat(response.getBody()).containsEntry("status", 503);
    }

    @Test
    @DisplayName("BadCredentialsException → 401 UNAUTHORIZED (mesaj sabit Türkçe)")
    void handleBadCredentials_returns401() {
        // when
        ResponseEntity<Map<String, Object>> response =
                handler.handleBadCredentials(new BadCredentialsException("ignored"));

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("error", "Kullanıcı adı veya parola hatalı");
    }

    @Test
    @DisplayName("AccessDeniedException → 403 FORBIDDEN")
    void handleAccessDenied_returns403() {
        // when
        ResponseEntity<Map<String, Object>> response =
                handler.handleAccessDenied(new AccessDeniedException("denied"));

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("error", "Yetkisiz işlem");
    }

    @Test
    @DisplayName("ObjectOptimisticLockingFailureException → 409 + STALE_VERSION code")
    void handleStaleVersion_returns409WithStaleCode() {
        // when
        ResponseEntity<Map<String, Object>> response = handler.handleStaleVersion(
                new ObjectOptimisticLockingFailureException("Flight", 1L));

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("code", "STALE_VERSION");
    }

    @Test
    @DisplayName("MethodArgumentNotValidException → 400 + fields map field hatalarını içerir")
    void handleValidation_returns400WithFieldErrors() throws Exception {
        // given
        MethodArgumentNotValidException ex = buildValidationException(
                new FieldError("createFlightRequest", "flightNumber", "Geçersiz format"),
                new FieldError("createFlightRequest", "originStation", "ICAO 4 büyük harf olmalı")
        );

        // when
        ResponseEntity<Map<String, Object>> response = handler.handleValidation(ex);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("status", 400);
        assertThat(response.getBody()).containsEntry("error", "Doğrulama hatası");

        @SuppressWarnings("unchecked")
        Map<String, String> fields = (Map<String, String>) response.getBody().get("fields");
        assertThat(fields)
                .containsEntry("flightNumber", "Geçersiz format")
                .containsEntry("originStation", "ICAO 4 büyük harf olmalı");
    }

    @Test
    @DisplayName("Validasyon → defaultMessage null ise fallback 'Geçersiz değer' yazılır")
    void handleValidation_nullDefaultMessage_fallbacks() throws Exception {
        // given
        MethodArgumentNotValidException ex = buildValidationException(
                new FieldError("createFlightRequest", "airlineCode", null)
        );

        // when
        ResponseEntity<Map<String, Object>> response = handler.handleValidation(ex);

        // then
        @SuppressWarnings("unchecked")
        Map<String, String> fields = (Map<String, String>) response.getBody().get("fields");
        assertThat(fields).containsEntry("airlineCode", "Geçersiz değer");
    }

    private MethodArgumentNotValidException buildValidationException(FieldError... errors) throws Exception {
        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(new Object(), "createFlightRequest");
        for (FieldError fe : errors) {
            bindingResult.addError(fe);
        }
        MethodParameter param = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("dummy"), -1);
        return new MethodArgumentNotValidException(param, bindingResult);
    }

    @SuppressWarnings("unused")
    private void dummy() {}
}
