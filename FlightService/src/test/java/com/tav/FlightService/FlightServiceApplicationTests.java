package com.tav.FlightService;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.liquibase.enabled=false",
        "spring.kafka.bootstrap-servers=",
        "app.jwt.secret=test-secret-key-minimum-32-bytes-ok!",
        "app.jwt.expiration-ms=3600000"
})
class FlightServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
