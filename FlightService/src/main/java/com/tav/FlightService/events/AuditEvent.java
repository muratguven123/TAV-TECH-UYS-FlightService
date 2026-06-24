package com.tav.FlightService.events;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * flight.audit Kafka topic payload'ı.
 *
 * payload alanı JsonNode tipindedir:
 *   - Başarıda: metodun dönüş değerinin Jackson serializasyonu
 *   - Başarısızlıkta: null (JSON null olarak yazılır)
 *
 * JsonNode kullanımının avantajları:
 *   1. Tip bilgisi Jackson tarafından yönetilir — Object gibi belirsiz değil
 *   2. Consumer tarafı kendi POJO'suna kolayca deserialize edebilir
 *   3. Kafka'ya yazılırken ek tip header'ı gerekmez
 */
public record AuditEvent(
        String action,
        AuditCategory category,
        String username,
        String result,
        Instant occurredAt,
        JsonNode payload
) {}
