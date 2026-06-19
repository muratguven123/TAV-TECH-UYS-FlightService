package com.tav.FlightService.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class GatewayAuthFilter extends OncePerRequestFilter {

    @Value("${app.gateway.secret}")
    private String expectedGatewaySecret;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String gatewaySecret = request.getHeader("X-Gateway-Secret");
        if (!StringUtils.hasText(gatewaySecret) || !gatewaySecret.equals(expectedGatewaySecret)) {
            log.warn("Geçersiz X-Gateway-Secret: {}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String username = request.getHeader("X-User-Name");
        String rolesHeader = request.getHeader("X-User-Roles");

        if (!StringUtils.hasText(username)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        Set<SimpleGrantedAuthority> authorities = StringUtils.hasText(rolesHeader)
                ? Arrays.stream(rolesHeader.split(","))
                        .map(String::trim)
                        .filter(StringUtils::hasText)
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toSet())
                : Set.of();

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(username, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);

        filterChain.doFilter(request, response);
    }
}
