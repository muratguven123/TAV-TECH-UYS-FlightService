package com.tav.FlightService.audit;

import com.tav.FlightService.events.AuditCategory;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * AOP audit anotasyonu.
 *
 * ⚠️  Self-invocation tuzağı: Bu anotasyonu taşıyan metodlar yalnızca harici
 *     bean'lerden (controller, başka servis) çağrılmalıdır.  Aynı sınıf içinden
 *     this.someMethod() şeklinde yapılan çağrılar Spring proxy'sini bypass eder;
 *     AuditAspect ÇALIŞMAZ.  Bkz. README — "AOP Audit — Self-Invocation Tuzağı".
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    String action();

    AuditCategory category() default AuditCategory.FLIGHT;
}
