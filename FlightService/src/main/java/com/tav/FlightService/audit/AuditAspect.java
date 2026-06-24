package com.tav.FlightService.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tav.FlightService.common.SecurityUtils;
import com.tav.FlightService.events.AuditEvent;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * @Auditable anotasyonu taşıyan metodları kesip flight.audit topic'ine yazar.
 *
 * Başarı  → result nesnesi JsonNode'a dönüştürülür (tip güvenli serializasyon).
 * Başarısızlık → payload=null, result="FAILURE:<ExceptionClassName>".
 * Exception bu aspect tarafından YUTULMAZ — çağırıcıya propagate edilir.
 *
 * ⚠️  Self-invocation tuzağı: aynı sınıf içinden this.xxx() çağrısı proxy'yi
 * bypass eder; aspect çalışmaz. Bkz. README — "AOP Audit — Self-Invocation Tuzağı".
 */
@Slf4j
@Aspect
@Component
public class AuditAspect {

    static final String TOPIC = "flight.audit";

    private final KafkaTemplate<String, AuditEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public AuditAspect(
            @Qualifier("auditKafkaTemplate") KafkaTemplate<String, AuditEvent> kafkaTemplate,
            ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper  = objectMapper;
    }

    @AfterReturning(pointcut = "@annotation(auditable)", returning = "result")
    public void afterSuccess(JoinPoint jp, Auditable auditable, Object result) {
        // Object → JsonNode: tip bilgisi korunur, Jackson serializasyonu deterministik
        JsonNode payloadNode = objectMapper.valueToTree(result);

        AuditEvent event = new AuditEvent(
                auditable.action(),
                auditable.category(),
                SecurityUtils.currentUsername(),
                "SUCCESS",
                Instant.now(),
                payloadNode
        );
        sendAudit(auditable.action(), event);
    }

    @AfterThrowing(pointcut = "@annotation(auditable)", throwing = "ex")
    public void afterFailure(JoinPoint jp, Auditable auditable, Throwable ex) {
        AuditEvent event = new AuditEvent(
                auditable.action(),
                auditable.category(),
                SecurityUtils.currentUsername(),
                "FAILURE:" + ex.getClass().getSimpleName(),
                Instant.now(),
                null          // hata durumunda payload yok
        );
        sendAudit(auditable.action(), event);
        // Exception yutulmaz — controller'a / GlobalExceptionHandler'a ulaşır
    }

    private void sendAudit(String action, AuditEvent event) {
        kafkaTemplate.send(TOPIC, action, event)
                .whenComplete((r, ex) -> {
                    if (ex != null) {
                        log.error("flight.audit Kafka send failed action={}", action, ex);
                    }
                });
    }
}
