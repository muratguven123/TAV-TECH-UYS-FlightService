package com.tav.FlightService.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Flight Service → Reference Manager servis-servis iletişimi.
 *
 * DEF-005 / TD-008 FIX: connect=2s, read=5s timeout eklendi.
 * ReferenceManager yavaşladığında thread'ler artık sonsuza beklemez;
 * timeout sonrası ResourceAccessException fırlatılır → FlightService
 * ServiceUnavailableException (503) ile karşılık verir.
 */
@Configuration
public class RestClientConfig {

    // DEF-005: Connect timeout — TCP bağlantısı kurulmazsa bu sürede vazgeç
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    // DEF-005: Read timeout — bağlantı kuruldu ama yanıt gelmezse bu sürede vazgeç
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    public RestClient referenceManagerRestClient(
            @Value("${app.reference.base-url}") String baseUrl,
            @Value("${app.gateway.secret}") String gatewaySecret,
            @Value("${app.internal-user.username}") String serviceUsername) {

        // DEF-005 FIX: SimpleClientHttpRequestFactory ile timeout yapılandırması
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        requestFactory.setReadTimeout((int) READ_TIMEOUT.toMillis());

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().set("X-Gateway-Secret", gatewaySecret);
                    request.getHeaders().set("X-User-Name", serviceUsername);
                    request.getHeaders().set("X-User-Roles", "ROLE_OPERATION_OFFICER");
                    return execution.execute(request, body);
                })
                .build();
    }
}
