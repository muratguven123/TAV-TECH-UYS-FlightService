package com.tav.FlightService;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"reference.events"})
@TestPropertySource(properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
        "app.jwt.secret=test-secret-key-minimum-32-bytes-ok!",
        "app.jwt.expiration-ms=3600000"
})
class FlightServiceApplicationTests {

    @org.springframework.boot.test.mock.mockito.MockBean
    org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @Test
    void contextLoads() {
    }
}
