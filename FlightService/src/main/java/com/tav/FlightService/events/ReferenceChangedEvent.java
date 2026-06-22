package com.tav.FlightService.events;

public record ReferenceChangedEvent(
        ReferenceEntityType entityType,
        ChangeType changeType,
        String businessKey,
        Object payload
) {
    public String partitionKey() {
        return entityType.name() + ":" + businessKey;
    }
}
