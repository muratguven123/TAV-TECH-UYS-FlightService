package com.tav.FlightService.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Flight Service → Reference Manager servis-servis iletişimi.
 *
 * RM, gateway header tabanlı güven modeli kullanır (Bearer token değil):
 *   X-Gateway-Secret  — paylaşılan iç sır
 *   X-User-Name       — servis hesabı adı
 *   X-User-Roles      — sabit ROLE_OPERATION_OFFICER
 *
 * Bu interceptor her istekte bu üç header'ı ekler; ayrıca token
 * yönetimine gerek yoktur.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient referenceManagerRestClient(
            @Value("${app.reference.base-url}") String baseUrl,
            @Value("${app.gateway.secret}") String gatewaySecret,
            @Value("${app.internal-user.username}") String serviceUsername) {

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().set("X-Gateway-Secret", gatewaySecret);
                    request.getHeaders().set("X-User-Name", serviceUsername);
                    request.getHeaders().set("X-User-Roles", "ROLE_OPERATION_OFFICER");
                    return execution.execute(request, body);
                })
                .build();
    }
}
