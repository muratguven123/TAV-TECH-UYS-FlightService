package com.tav.FlightService.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.util.List;
import java.util.Map;

/**
 * Test classpath'inde otomatik yüklenir — Keycloak JWKS bağımlılığı olmadan JWT doğrulama.
 */
@AutoConfiguration
public class MockJwtDecoderAutoConfiguration {

    public static final String VALID_TEST_TOKEN = "valid-test-token";

    @Bean
    @Primary
    JwtDecoder jwtDecoder() {
        return token -> {
            if (!VALID_TEST_TOKEN.equals(token)) {
                throw new BadJwtException("invalid test token");
            }
            return Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject("ali")
                    .claim("preferred_username", "ali")
                    .claim("realm_access", Map.of("roles", List.of("OPERATION_OFFICER")))
                    .build();
        };
    }
}
