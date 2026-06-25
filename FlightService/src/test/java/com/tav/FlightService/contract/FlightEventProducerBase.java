package com.tav.FlightService.contract;

import com.tav.FlightService.domain.FlightType;
import com.tav.FlightService.dto.FlightResponse;
import com.tav.FlightService.events.FlightChangeType;
import com.tav.FlightService.events.FlightChangedEvent;
import com.tav.FlightService.events.FlightEventKafkaRelay;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.verifier.messaging.boot.AutoConfigureMessageVerifier;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * flight.events producer kontratları için SCC base class.
 *
 * <p>SCC tarafından oluşturulan test sınıfları bu sınıfı extend eder.
 * Her trigger metodu, {@link FlightEventKafkaRelay}'i doğrudan çağırarak
 * {@code flight.events} topic'ine mesaj gönderir. {@code @TransactionalEventListener}
 * proxy'si devreye girmez — metot gövdesi direkt yürütülür.
 *
 * <p><b>NOT:</b> {@link com.tav.FlightService.util.TestDataFactory#FLIGHT_NO} "TK001"
 * (3 rakam) içerdiğinden domain validation ile uyumsuz; burada geçerli 4 rakamlı
 * format "TK0001" kullanılır. TestDataFactory ileride güncellenmeli.
 *
 * <p><b>Teknik Borç:</b> {@code FlightChangedEvent} yalnızca bu modülde tanımlı;
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:h2:mem:fs-contract-db;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "app.gateway.auth.enabled=false",
        "app.reference.validator=noop",
        "spring.kafka.producer.properties.spring.json.add.type.headers=false"
})
@AutoConfigureMessageVerifier
@EmbeddedKafka(
        topics = {"flight.events"},
        partitions = 1,
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@TestPropertySource(locations = "classpath:/contract-test.properties")
public abstract class FlightEventProducerBase {

    /** Kontrat regex ^[A-Z]{2}\d{4}$ ile uyumlu test verisi */
    static final FlightResponse SAMPLE_FLIGHT = new FlightResponse(
            1L,
            "TK0001",                        // 2 harf + 4 rakam
            "TK",
            "TC-JFA",
            "LTFM",
            "LTAC",
            FlightType.PASSENGER,
            LocalDateTime.of(2026, 6, 24, 10, 0),
            LocalDateTime.of(2026, 6, 24, 11, 30),
            LocalDate.of(2026, 6, 24),
            0L
    );

    @Autowired
    private FlightEventKafkaRelay relay;

    @BeforeEach
    public void setup() {
        // no-op
    }

    /** SCC tarafından triggerFlightCreated() label'ıyla tetiklenir. */
    public void triggerFlightCreated() {
        relay.onFlightChanged(new FlightChangedEvent(
                FlightChangeType.CREATED, 1L, 0L, "test-user",
                Instant.parse("2026-06-24T10:00:00Z"), SAMPLE_FLIGHT));
    }

    /** SCC tarafından triggerFlightUpdated() label'ıyla tetiklenir. */
    public void triggerFlightUpdated() {
        FlightResponse updated = new FlightResponse(
                1L, "TK0001", "TK", "TC-JFA", "LTFM", "LTAC",
                FlightType.PASSENGER,
                LocalDateTime.of(2026, 6, 24, 10, 30),
                LocalDateTime.of(2026, 6, 24, 12, 0),
                LocalDate.of(2026, 6, 24),
                1L
        );
        relay.onFlightChanged(new FlightChangedEvent(
                FlightChangeType.UPDATED, 1L, 1L, "test-user",
                Instant.parse("2026-06-24T10:05:00Z"), updated));
    }

    /** SCC tarafından triggerFlightDeleted() label'ıyla tetiklenir. */
    public void triggerFlightDeleted() {
        FlightResponse atDeletion = new FlightResponse(
                1L, "TK0001", "TK", "TC-JFA", "LTFM", "LTAC",
                FlightType.PASSENGER,
                LocalDateTime.of(2026, 6, 24, 10, 0),
                LocalDateTime.of(2026, 6, 24, 11, 30),
                LocalDate.of(2026, 6, 24),
                2L
        );
        relay.onFlightChanged(new FlightChangedEvent(
                FlightChangeType.DELETED, 1L, 2L, "test-user",
                Instant.parse("2026-06-24T10:10:00Z"), atDeletion));
    }
}
