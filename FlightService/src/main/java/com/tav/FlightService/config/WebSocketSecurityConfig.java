package com.tav.FlightService.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * STOMP inbound channel güvenliği.
 *
 * @EnableWebSocketMessageBroker bu sınıfta YOK — sadece WebSocketConfig'te bulunur.
 * İki kez declare edilmesi Spring'in broker konfigürasyonunu bozar.
 *
 * STOMP CONNECT frame'inde beklenen header'lar:
 *   X-Gateway-Secret : <secret>          (zorunlu)
 *   X-User-Name      : <username>        (zorunlu)
 *   X-User-Roles     : ROLE_OPS,ROLE_BI  (opsiyonel, virgülle ayrılmış)
 */
@Slf4j
@Configuration
public class WebSocketSecurityConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${app.gateway.secret}")
    private String expectedGatewaySecret;

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                // Sadece CONNECT frame'ini denetle; diğer frame'ler geçer
                if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
                    return message;
                }

                String secret   = accessor.getFirstNativeHeader("X-Gateway-Secret");
                String username = accessor.getFirstNativeHeader("X-User-Name");
                String rolesHdr = accessor.getFirstNativeHeader("X-User-Roles");

                // Timing-safe karşılaştırma — secret'ın uzunluğunu sızdırmaz
                if (!StringUtils.hasText(secret)
                        || !MessageDigest.isEqual(
                                secret.getBytes(StandardCharsets.UTF_8),
                                expectedGatewaySecret.getBytes(StandardCharsets.UTF_8))) {
                    log.warn("WebSocket CONNECT reddedildi: geçersiz X-Gateway-Secret");
                    throw new MessageDeliveryException(
                            message, "WebSocket kimlik doğrulama başarısız: geçersiz secret");
                }

                if (!StringUtils.hasText(username)) {
                    log.warn("WebSocket CONNECT reddedildi: X-User-Name eksik");
                    throw new MessageDeliveryException(
                            message, "WebSocket kimlik doğrulama başarısız: kullanıcı adı eksik");
                }

                List<SimpleGrantedAuthority> authorities =
                        StringUtils.hasText(rolesHdr)
                                ? List.of(rolesHdr.split(",")).stream()
                                        .map(String::trim)
                                        .filter(StringUtils::hasText)
                                        .map(SimpleGrantedAuthority::new)
                                        .toList()
                                : List.of();

                accessor.setUser(new UsernamePasswordAuthenticationToken(username, null, authorities));
                log.debug("WebSocket CONNECT onaylandı: user={}", username);
                return message;
            }
        });
    }
}
