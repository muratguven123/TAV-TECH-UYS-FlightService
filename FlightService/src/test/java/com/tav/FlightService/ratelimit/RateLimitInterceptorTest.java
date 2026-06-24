package com.tav.FlightService.ratelimit;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitInterceptorTest {

    private RateLimitInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new RateLimitInterceptor();
        authenticate("alice");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("preHandle → limit altında true döner")
    void preHandle_underLimit_returnsTrue() throws Exception {
        // when / then
        assertThat(interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), null))
                .isTrue();
    }

    @Test
    @DisplayName("preHandle → 30. istek dahil true, 31. istek → 429 ve false döner")
    void preHandle_atLimit_returns429() throws Exception {
        // given — 30 istek harca
        for (int i = 0; i < 30; i++) {
            assertThat(interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), null))
                    .as("istek %d limit içinde olmalı", i + 1)
                    .isTrue();
        }

        // when — 31. istek
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean allowed = interceptor.preHandle(new MockHttpServletRequest(), response, null);

        // then
        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(response.getContentType()).contains("application/json");
        assertThat(response.getContentAsString())
                .contains("\"status\":429")
                .contains("Dakikalık istek limiti");
    }

    @Test
    @DisplayName("preHandle → farklı kullanıcılar ayrı bucket'a sahiptir (bir kullanıcı limit aştığında diğeri etkilenmez)")
    void preHandle_differentUsers_haveSeparateBuckets() throws Exception {
        // given — alice 30 isteği harcasın
        for (int i = 0; i < 30; i++) {
            interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), null);
        }
        // alice limiti aşmalı
        assertThat(interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), null))
                .isFalse();

        // when — bob girer
        authenticate("bob");
        boolean bobAllowed = interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), null);

        // then
        assertThat(bobAllowed).as("bob'un kendi bucket'ı henüz boş olmamalı").isTrue();
    }

    @Test
    @DisplayName("preHandle → kimliği bilinmeyen istek 'anonymous' bucket'ına düşer")
    void preHandle_anonymous_usesAnonymousBucket() throws Exception {
        // given
        SecurityContextHolder.clearContext();

        // when
        boolean allowed = interceptor.preHandle(
                new MockHttpServletRequest(), new MockHttpServletResponse(), null);

        // then
        assertThat(allowed).isTrue();
    }

    @Test
    @DisplayName("preHandle → 429 body GlobalExceptionHandler formatıyla uyumlu (timestamp + status + error)")
    void preHandle_429Body_matchesGlobalExceptionHandlerFormat() throws Exception {
        // given — limit aş
        for (int i = 0; i < 30; i++) {
            interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), null);
        }

        // when
        MockHttpServletResponse response = new MockHttpServletResponse();
        interceptor.preHandle(new MockHttpServletRequest(), response, null);

        // then
        String body = response.getContentAsString();
        assertThat(body)
                .contains("timestamp")
                .contains("status")
                .contains("error");
    }

    private void authenticate(String username) {
        var auth = new UsernamePasswordAuthenticationToken(
                username, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_OPERATION_OFFICER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
