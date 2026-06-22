package com.tav.FlightService.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kullanıcı başına dakikada 30 istek sınırı (token bucket).
 *
 * <p><b>Bilinen kısıt (multi-pod):</b> Her pod kendi ConcurrentHashMap'ini tutar.
 * N pod çalıştığında efektif limit N×30 olur. Dağıtık rate limiting için
 * Bucket4j'nin Redis/Hazelcast entegrasyonu kullanılmalıdır (Aşama 6+).
 *
 * <p><b>Bellek uyarısı (TODO):</b> ConcurrentHashMap sınırsız büyür.
 * Production'da Caffeine veya zamanlı temizlik mekanizmasıyla
 * boyut sınırı eklenmeli; aksi takdirde OOM riski doğar.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final int MAX_REQUESTS_PER_MINUTE = 30;

    // TODO: Production'da Caffeine gibi bounded/evicting bir cache kullanın.
    // Mevcut ConcurrentHashMap boyutsuz büyür; uzun süreli çalışmada bellek sızıntısına yol açar.
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {

        String username = resolveUsername();
        Bucket bucket = buckets.computeIfAbsent(username, this::newBucket);

        if (bucket.tryConsume(1)) {
            return true;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        // GlobalExceptionHandler formatıyla uyumlu: timestamp + status + error
        response.getWriter().write(
            "{\"timestamp\":\"" + Instant.now() + "\","
            + "\"status\":429,"
            + "\"error\":\"Dakikalık istek limiti aşıldı (maks 30)\"}"
        );
        return false;
    }

    private Bucket newBucket(String username) {
        Bandwidth limit = Bandwidth.builder()
            .capacity(MAX_REQUESTS_PER_MINUTE)
            .refillGreedy(MAX_REQUESTS_PER_MINUTE, Duration.ofMinutes(1))
            .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private String resolveUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return "anonymous";
    }
}
