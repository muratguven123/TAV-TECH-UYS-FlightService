package com.tav.FlightService.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketSecurityConfigTest {

    @Mock JwtDecoder jwtDecoder;
    @Mock MessageChannel channel;

    ChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        WebSocketSecurityConfig config = new WebSocketSecurityConfig(jwtDecoder);
        ChannelRegistration registration = new ChannelRegistration();
        config.configureClientInboundChannel(registration);
        @SuppressWarnings("unchecked")
        List<ChannelInterceptor> interceptors =
                (List<ChannelInterceptor>) ReflectionTestUtils.getField(registration, "interceptors");
        interceptor = interceptors.getFirst();
    }

    @Test
    @DisplayName("CONNECT — Bearer token yok → reddedilir")
    void connect_withoutToken_rejected() {
        assertThatThrownBy(() -> interceptor.preSend(connectMessage(Map.of()), channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("JWT gerekli");
    }

    @Test
    @DisplayName("CONNECT — geçersiz JWT → reddedilir")
    void connect_invalidToken_rejected() {
        when(jwtDecoder.decode(anyString())).thenThrow(new BadJwtException("bad"));

        assertThatThrownBy(() -> interceptor.preSend(
                connectMessage(Map.of("Authorization", "Bearer bad-token")), channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("geçersiz token");
    }

    @Test
    @DisplayName("SUBSCRIBE /topic/system.health — OPERATION_OFFICER reddedilir")
    void subscribe_systemHealth_withoutAdmin_rejected() {
        Jwt jwt = officerJwt();
        when(jwtDecoder.decode("good")).thenReturn(jwt);

        interceptor.preSend(connectMessage(Map.of("Authorization", "Bearer good")), channel);

        assertThatThrownBy(() -> interceptor.preSend(
                subscribeMessage("/topic/system.health", jwt), channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("Yetkisiz subscription");
    }

    private Message<?> connectMessage(Map<String, String> nativeHeaders) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        nativeHeaders.forEach(accessor::setNativeHeader);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<?> subscribeMessage(String destination, Jwt jwt) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setUser(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "ali",
                null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_OPERATION_OFFICER"))));
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static Jwt officerJwt() {
        return Jwt.withTokenValue("good")
                .header("alg", "none")
                .claim("preferred_username", "ali")
                .claim("realm_access", Map.of("roles", List.of("OPERATION_OFFICER")))
                .build();
    }
}
