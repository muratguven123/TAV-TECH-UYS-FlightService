package com.tav.FlightService.ratelimit;

// FIX: TD-011 — MAX_REQUESTS_PER_MINUTE hardcoded sabiti @Value injection ile değiştirildi.
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
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
 * Kullanıcı başına rate limit (token bucket).
 *
 * <p>Kapasite ve refill süresi {@code application.yml} üzerinden yapılandırılır:
 * <pre>
 * rate-limit:
 *   capacity: 30        # Varsayılan: 30 istek
 *   refill-seconds: 60  # Varsayılan: 60 saniye (1 dakika)
 * </pre>
 *
 * <p><b>Bilinen kısıt (multi-pod):</b> Her pod kendi ConcurrentHashMap'ini tutar.
 * N pod çalıştığında efektif limit N×capacity olur. Dağıtık rate limiting için
 * Bucket4j'nin Redis/Hazelcast entegrasyonu kullanılmalıdır (Aşama 6+).
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    // FIX: TD-011 — hardcoded 30 yerine @Value ile config'den okunur
    @Value("${rate-limit.capacity:30}")
    private int capacity;

    @Value("${rate-limit.refill-seconds:60}")
    private int refillSeconds;

    // TODO: Production'da Caffeine gibi bounded/evicting bir cache kullanın.
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
        response.getWriter().write(
            "{\"timestamp\":\"" + Instant.now() + "\","
            + "\"status\":429,"
            + "\"error\":\"Dakikalık istek limiti aşıldı (maks " + capacity + ")\"}"
        );
        return false;
    }

    private Bucket newBucket(String username) {
        Bandwidth limit = Bandwidth.builder()
            .capacity(capacity)
            .refillGreedy(capacity, Duration.ofSeconds(refillSeconds))
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
