package com.tav.FlightService.common;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * SecurityContextHolder'dan mevcut kullanıcı adını çeken yardımcı sınıf.
 *
 * Overload'lu test edilebilirlik: Authentication nesnesi inject edilebilir.
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    public static String currentUsername() {
        return currentUsername(SecurityContextHolder.getContext().getAuthentication());
    }

    public static String currentUsername(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return "system";
        }
        return authentication.getName();
    }
}
