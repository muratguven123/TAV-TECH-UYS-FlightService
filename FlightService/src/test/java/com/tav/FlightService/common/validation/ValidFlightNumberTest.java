package com.tav.FlightService.common.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ValidFlightNumberTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void initValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeFactory() {
        factory.close();
    }

    static class Holder {
        @ValidFlightNumber
        String value;

        Holder(String value) {
            this.value = value;
        }
    }

    // ------------------------------------------------------------------ Geçerli

    @ParameterizedTest
    @ValueSource(strings = {
            "TK0001",
            "TK1234",
            "LH9999",
            "AA0000",
            "ZZ9876"
    })
    @DisplayName("Geçerli uçuş numaraları (2 büyük harf + 4 rakam) violation üretmez")
    void validFlightNumbers_haveNoViolations(String input) {
        Set<ConstraintViolation<Holder>> violations = validator.validate(new Holder(input));
        assertThat(violations).as("input='%s'", input).isEmpty();
    }

    // ------------------------------------------------------------------ Geçersiz

    @ParameterizedTest
    @ValueSource(strings = {
            "tk0001",       // küçük harf
            "TK001",        // 3 rakam (eksik)
            "TK00001",      // 5 rakam (fazla)
            "T0001",        // 1 harf
            "TKK0001",      // 3 harf
            "TK00A1",       // rakam yerine harf
            "12K001",       // baş rakam
            "TK-001",       // ayraç
            "TK 001",       // boşluk
            "AAAA1234",     // 4 harf
            "TK01"          // çok kısa
    })
    @DisplayName("Geçersiz uçuş numaraları violation üretir")
    void invalidFlightNumbers_haveViolations(String input) {
        Set<ConstraintViolation<Holder>> violations = validator.validate(new Holder(input));
        assertThat(violations).as("input='%s'", input).isNotEmpty();
        assertThat(violations.iterator().next().getMessage())
                .contains("Uçuş numarası");
    }

    // ------------------------------------------------------------------ Null/empty davranışı

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("Null ve boş string Pattern semantiği gereği violation üretmez (Pattern null'a geçirir, boş için regex eşleşmez)")
    void nullOrEmptyValue_patternSemantics(String input) {
        Set<ConstraintViolation<Holder>> violations = validator.validate(new Holder(input));
        // Pattern null değeri valid sayar; boş string ise regex'e uymaz → violation üretir.
        // Bu davranış @ValidFlightNumber'ın @NotBlank ile birlikte kullanılmasını gerektirir.
        if (input == null) {
            assertThat(violations).isEmpty();
        } else {
            assertThat(violations).isNotEmpty();
        }
    }

    @Test
    @DisplayName("@ReportAsSingleViolation → tek violation döner (meta-constraint zinciri toplanır)")
    void invalidValue_reportsSingleViolation() {
        Set<ConstraintViolation<Holder>> violations = validator.validate(new Holder("INVALID"));
        assertThat(violations).hasSize(1);
    }
}
