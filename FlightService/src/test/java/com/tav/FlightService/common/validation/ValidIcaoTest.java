package com.tav.FlightService.common.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ValidIcaoTest {

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
        @ValidIcao
        String value;

        Holder(String value) {
            this.value = value;
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "LTFM",
            "LTAC",
            "LFPG",
            "KJFK",
            "EGLL",
            "RJTT",
            "ABCD"
    })
    @DisplayName("Geçerli ICAO kodları (4 büyük harf) violation üretmez")
    void validIcao_noViolations(String input) {
        Set<ConstraintViolation<Holder>> violations = validator.validate(new Holder(input));
        assertThat(violations).as("input='%s'", input).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ltfm",      // küçük harf
            "LTF",       // 3 harf (eksik)
            "LTFMM",     // 5 harf (fazla)
            "LTF1",      // rakam içeriyor
            "12FM",      // rakam başta
            "LT-M",      // ayraç
            "LT M",      // boşluk
            "IST",       // 3 harf (IATA)
            "ISTAN",     // 5 harf
            "12A",       // rakam + harf karışık
            "Ltfm"       // sadece ilk büyük
    })
    @DisplayName("Geçersiz ICAO kodları violation üretir")
    void invalidIcao_hasViolations(String input) {
        Set<ConstraintViolation<Holder>> violations = validator.validate(new Holder(input));
        assertThat(violations).as("input='%s'", input).isNotEmpty();
        assertThat(violations.iterator().next().getMessage())
                .contains("ICAO");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("Null violation üretmez (Pattern semantiği), boş string ise violation üretir")
    void nullOrEmpty_patternSemantics(String input) {
        Set<ConstraintViolation<Holder>> violations = validator.validate(new Holder(input));
        if (input == null) {
            assertThat(violations).isEmpty();
        } else {
            assertThat(violations).isNotEmpty();
        }
    }
}
