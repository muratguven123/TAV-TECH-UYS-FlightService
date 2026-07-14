package com.tav.FlightService.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket broker konfigürasyonu.
 *
 * Endpoint  : /ws  (native WebSocket — SockJS HTTP fallback yok)
 * Broker    : /topic/**  (simple in-memory veya RabbitMQ STOMP relay — nokta notasyonu)
 * App prefix: /app/**
 *
 * {@code app.websocket.relay.enabled=true} olduğunda RabbitMQ STOMP relay kullanılır;
 * çoklu FlightService instance'larında mesajlar tüm abonelere ulaşır.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${app.websocket.allowed-origins}")
    private String[] allowedOrigins;

    @Value("${app.websocket.relay.enabled:false}")
    private boolean relayEnabled;

    @Value("${app.websocket.relay.host:rabbitmq}")
    private String relayHost;

    @Value("${app.websocket.relay.port:61613}")
    private int relayPort;

    @Value("${app.websocket.relay.username:guest}")
    private String relayUsername;

    @Value("${app.websocket.relay.password:guest}")
    private String relayPassword;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        if (relayEnabled) {
            registry.enableStompBrokerRelay("/topic")
                    .setRelayHost(relayHost)
                    .setRelayPort(relayPort)
                    .setClientLogin(relayUsername)
                    .setClientPasscode(relayPassword)
                    .setSystemLogin(relayUsername)
                    .setSystemPasscode(relayPassword)
                    .setSystemHeartbeatSendInterval(10000)
                    .setSystemHeartbeatReceiveInterval(10000);
        } else {
            registry.enableSimpleBroker("/topic")
                    .setHeartbeatValue(new long[]{10000, 10000})
                    .setTaskScheduler(heartbeatScheduler());
        }
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Bean
    public TaskScheduler heartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins);
    }
}
