import org.springframework.cloud.contract.spec.Contract

/**
 * flight.events :: UPDATED
 *
 * CREATED ile aynı şema; changeType ayrımı FAS'ın eventType enum parse'ını kontrol eder.
 */
Contract.make {
    label("triggerFlightUpdated")
    input {
        triggeredBy("triggerFlightUpdated()")
    }
    outputMessage {
        sentTo("flight.events")
        headers {
            header("kafka_messageKey", $(
                producer(regex("[0-9]+")),
                consumer("1")
            ))
        }
        body([
            changeType        : $(producer(regex("CREATED|UPDATED|DELETED")), consumer("UPDATED")),
            flightId          : $(producer(regex("[0-9]+")),                  consumer(1)),
            version           : $(producer(regex("[0-9]+")),                  consumer(1)),
            username          : $(producer(regex(".+")),                       consumer("test-user")),
            occurredAt        : $(producer(regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z")),
                                   consumer("2026-06-24T10:05:00Z")),
            sequence          : $(producer(regex("[0-9]+")),                  consumer(2)),
            payload: [
                id               : $(producer(regex("[0-9]+")),                    consumer(1)),
                flightNumber     : $(producer(regex("^[A-Z]{2}\\d{4}\$")),         consumer("TK0001")),
                airlineCode      : $(producer(regex("^[A-Z]{2}\$")),               consumer("TK")),
                aircraftTailNumber: $(producer(regex(".+")),                        consumer("TC-JFA")),
                originStation    : $(producer(regex("^[A-Z]{4}\$")),               consumer("LTFM")),
                destinationStation: $(producer(regex("^[A-Z]{4}\$")),              consumer("LTAC")),
                flightType       : $(producer(regex("PASSENGER|CARGO|POSITION")),  consumer("PASSENGER")),
                scheduledDeparture: $(producer(regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")),
                                      consumer("2026-06-24T10:30:00")),
                scheduledArrival : $(producer(regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")),
                                      consumer("2026-06-24T12:00:00")),
                flightDate       : $(producer(regex("\\d{4}-\\d{2}-\\d{2}")),      consumer("2026-06-24")),
                version          : $(producer(regex("[0-9]+")),                    consumer(1))
            ]
        ])
    }
}
