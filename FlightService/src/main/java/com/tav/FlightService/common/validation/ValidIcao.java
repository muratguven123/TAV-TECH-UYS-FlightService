package com.tav.FlightService.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Pattern(regexp = "^[A-Z]{4}$")
@ReportAsSingleViolation
public @interface ValidIcao {
    String message() default "ICAO kodu 4 büyük harften oluşmalı";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
