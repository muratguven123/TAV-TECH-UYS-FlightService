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
@Pattern(regexp = "^[A-Z]{2}\\d{4}$")
@ReportAsSingleViolation
public @interface ValidFlightNumber {
    String message() default "Uçuş numarası 'AA9999' formatında olmalı (2 büyük harf + 4 rakam)";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
