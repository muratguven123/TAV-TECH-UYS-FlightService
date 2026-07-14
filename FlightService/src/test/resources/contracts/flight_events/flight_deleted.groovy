import org.springframework.cloud.contract.spec.Contract

/**
 * flight.events :: DELETED
 *
 * Silme event'lerinde payload gönderilir (FlightService her zaman payload ekler).
 * FAS, DELETED event'ini de arşivler — tombstone'dan önce son bilinen durum.
 */
Contract.make {
    label("triggerFlightDeleted")
    input {
        triggeredBy("triggerFlightDeleted()")
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
            changeType        : $(producer(regex("CREATED|UPDATED|DELETED")), consumer("DELETED")),
            flightId          : $(producer(regex("[0-9]+")),                  consumer(1)),
            version           : $(producer(regex("[0-9]+")),                  consumer(2)),
            username          : $(producer(regex(".+")),                       consumer("test-user")),
            occurredAt        : $(producer(regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z")),
                                   consumer("2026-06-24T10:10:00Z")),
            sequence          : $(producer(regex("[0-9]+")),                  consumer(3)),
            payload: [
                id               : $(producer(regex("[0-9]+")),                    consumer(1)),
                flightNumber     : $(producer(regex("^[A-Z]{2}\\d{4}\$")),         consumer("TK0001")),
                airlineCode      : $(producer(regex("^[A-Z]{2}\$")),               consumer("TK")),
                aircraftTailNumber: $(producer(regex(".+")),                        consumer("TC-JFA")),
                originStation    : $(producer(regex("^[A-Z]{4}\$")),               consumer("LTFM")),
                destinationStation: $(producer(regex("^[A-Z]{4}\$")),              consumer("LTAC")),
                flightType       : $(producer(regex("PASSENGER|CARGO|POSITION")),  consumer("PASSENGER")),
                scheduledDeparture: $(producer(regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")),
                                      consumer("2026-06-24T10:00:00")),
                scheduledArrival : $(producer(regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")),
                                      consumer("2026-06-24T11:30:00")),
                flightDate       : $(producer(regex("\\d{4}-\\d{2}-\\d{2}")),      consumer("2026-06-24")),
                version          : $(producer(regex("[0-9]+")),                    consumer(2))
            ]
        ])
    }
}
