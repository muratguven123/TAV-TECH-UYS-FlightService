import org.springframework.cloud.contract.spec.Contract

/**
 * flight.audit :: başarısız operasyon
 *
 * Hata durumunda payload null, result "FAILURE:<ExceptionClassName>" formatında.
 */
Contract.make {
    label("triggerAuditFailure")
    input {
        triggeredBy("triggerAuditFailure()")
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
            action     : $(producer(regex(".+")),                       consumer("CREATE_FLIGHT")),
            category   : $(producer(regex("FLIGHT|REFERENCE|AUTH")), consumer("FLIGHT")),
            username   : $(producer(regex(".+")),                       consumer("test-user")),
            result     : $(producer(regex("FAILURE:.+")),               consumer("FAILURE:BusinessException")),
            occurredAt : $(producer(regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z")),
                           consumer("2026-06-24T10:00:00Z")),
            payload    : null
        ])
    }
}
