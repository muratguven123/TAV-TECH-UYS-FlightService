import org.springframework.cloud.contract.spec.Contract

/**
 * flight.audit :: başarılı operasyon
 *
 * Consumer servisi bu projede mevcut değil.
 * Bu kontrat, AuditEvent şemasının producer-side drift'ini yakalar:
 * alan yeniden adlandırılırsa veya kaldırılırsa bu test fail eder.
 *
 * partition key = action (örn. "CREATE_FLIGHT")
 */
Contract.make {
    label("triggerAuditSuccess")
    input {
        triggeredBy("triggerAuditSuccess()")
    }
    outputMessage {
        sentTo("flight.audit")
        headers {
            header("kafka_messageKey", $(
                producer(regex(".+")),
                consumer("CREATE_FLIGHT")
            ))
        }
        body([
            action      : $(producer(regex(".+")),                      consumer("CREATE_FLIGHT")),
            category    : $(producer(regex("FLIGHT|REFERENCE|AUTH")), consumer("FLIGHT")),
            username    : $(producer(regex(".+")),                       consumer("test-user")),
            result      : $(producer(regex("SUCCESS")),                 consumer("SUCCESS")),
            occurredAt  : $(producer(regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z")),
                            consumer("2026-06-24T10:00:00Z")),
            payload     : $(producer(optional(anyNonEmptyString())),    consumer([id: 1, flightNumber: "TK0001"]))
        ])
    }
}
