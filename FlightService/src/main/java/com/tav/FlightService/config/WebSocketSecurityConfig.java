package com.tav.FlightService.config;

import com.tav.FlightService.security.StompJwtSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;
import java.util.List;

/**
 * STOMP inbound channel güvenliği.
 *
 * @EnableWebSocketMessageBroker bu sınıfta YOK — sadece WebSocketConfig'te bulunur.
 *
 * STOMP CONNECT frame'inde beklenen header:
 *   Authorization : Bearer &lt;jwt&gt;  (zorunlu — Keycloak JWT)
 *
 * Kullanıcı adı ve roller JWT claim'lerinden okunur; client header'larına güvenilmez.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketSecurityConfig implements WebSocketMessageBrokerConfigurer {

    private static final String ROLE_OPERATION_OFFICER = "ROLE_OPERATION_OFFICER";
    private static final String ROLE_BI_SPECIALIST = "ROLE_BI_SPECIALIST";
    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final JwtDecoder jwtDecoder;

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor == null) {
                    return message;
                }

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    Authentication auth = authenticateConnect(message, accessor);
                    accessor.setUser(auth);
                    log.debug("WebSocket CONNECT onaylandı: user={}", auth.getName());
                }

                if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    authorizeSubscribe(message, accessor);
                }

                return message;
            }
        });
    }

    private Authentication authenticateConnect(Message<?> message, StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            log.warn("WebSocket CONNECT reddedildi: Authorization Bearer token eksik");
            throw new MessageDeliveryException(message,
                    "WebSocket kimlik doğrulama başarısız: JWT gerekli");
        }

        String token = authHeader.substring("Bearer ".length()).trim();
        if (!StringUtils.hasText(token)) {
            log.warn("WebSocket CONNECT reddedildi: boş JWT");
            throw new MessageDeliveryException(message,
                    "WebSocket kimlik doğrulama başarısız: JWT gerekli");
        }

        try {
            Jwt jwt = jwtDecoder.decode(token);
            String username = StompJwtSupport.resolveUsername(jwt);
            List<SimpleGrantedAuthority> authorities = StompJwtSupport.resolveRoles(jwt);
            return new UsernamePasswordAuthenticationToken(username, null, authorities);
        } catch (JwtException ex) {
            log.warn("WebSocket CONNECT reddedildi: geçersiz JWT — {}", ex.getMessage());
            throw new MessageDeliveryException(message,
                    "WebSocket kimlik doğrulama başarısız: geçersiz token", ex);
        }
    }

    private void authorizeSubscribe(Message<?> message, StompHeaderAccessor accessor) {
        String dest = accessor.getDestination();
        if (dest == null) {
            return;
        }

        Authentication auth = (Authentication) accessor.getUser();

        if (dest.startsWith(StompTopics.BULK_PREFIX)) {
            requireAnyRole(message, accessor, auth, ROLE_OPERATION_OFFICER);
            return;
        }

        if (dest.equals(StompTopics.SYSTEM_HEALTH)) {
            requireAnyRole(message, accessor, auth, ROLE_ADMIN);
            return;
        }

        if (dest.equals(StompTopics.REFERENCE_CHANGED)) {
            requireAnyRole(message, accessor, auth, ROLE_OPERATION_OFFICER);
            return;
        }

        if (dest.equals(StompTopics.FLIGHTS_ALL)
                || dest.startsWith(StompTopics.FLIGHTS_AIRLINE_PREFIX)
                || dest.startsWith(StompTopics.FLIGHTS_STATION_PREFIX)) {
            requireAnyRole(message, accessor, auth, ROLE_OPERATION_OFFICER, ROLE_BI_SPECIALIST, ROLE_ADMIN);
        }
    }

    private void requireAnyRole(Message<?> message, StompHeaderAccessor accessor, Authentication auth, String... roles) {
        if (auth == null || !hasAnyRole(auth, roles)) {
            throw new MessageDeliveryException(message,
                    "Yetkisiz subscription: " + accessor.getDestination());
        }
    }

    private static boolean hasAnyRole(Authentication auth, String... roles) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> Arrays.asList(roles).contains(a.getAuthority()));
    }
}
