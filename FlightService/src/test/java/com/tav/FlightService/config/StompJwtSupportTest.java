package com.tav.FlightService.config;

import com.tav.FlightService.security.StompJwtSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StompJwtSupportTest {

    @Test
    @DisplayName("resolveUsername → preferred_username öncelikli")
    void resolveUsername_prefersPreferredUsername() {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject("sub-id")
                .claim("preferred_username", "ali")
                .build();

        assertThat(StompJwtSupport.resolveUsername(jwt)).isEqualTo("ali");
    }

    @Test
    @DisplayName("resolveRoles → ROLE_ prefix eklenir")
    void resolveRoles_addsRolePrefix() {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .claim("realm_access", Map.of("roles", List.of("OPERATION_OFFICER", "ROLE_ADMIN")))
                .build();

        assertThat(StompJwtSupport.resolveRoles(jwt))
                .extracting(a -> a.getAuthority())
                .containsExactlyInAnyOrder("ROLE_OPERATION_OFFICER", "ROLE_ADMIN");
    }
}
