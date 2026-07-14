package com.tav.FlightService.security;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * STOMP CONNECT frame'lerindeki JWT claim'lerinden kullanıcı ve rol bilgisi çıkarır.
 */
public final class StompJwtSupport {

    private StompJwtSupport() {
    }

    public static String resolveUsername(Jwt jwt) {
        String preferred = jwt.getClaimAsString("preferred_username");
        if (StringUtils.hasText(preferred)) {
            return preferred;
        }
        String sub = jwt.getSubject();
        return sub != null ? sub : "unknown";
    }

    @SuppressWarnings("unchecked")
    public static List<SimpleGrantedAuthority> resolveRoles(Jwt jwt) {
        Object realmAccessClaim = jwt.getClaim("realm_access");
        if (!(realmAccessClaim instanceof Map<?, ?> realmAccess)) {
            return List.of();
        }
        Object rolesObj = realmAccess.get("roles");
        if (!(rolesObj instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(Object::toString)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .map(SimpleGrantedAuthority::new)
                .toList();
    }
}
