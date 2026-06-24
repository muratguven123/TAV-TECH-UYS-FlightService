package com.tav.FlightService.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GatewayAuthFilterTest {

    private static final String SECRET = "supersecret";

    @Mock FilterChain filterChain;

    private GatewayAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new GatewayAuthFilter();
        ReflectionTestUtils.setField(filter, "expectedGatewaySecret", SECRET);
        ReflectionTestUtils.setField(filter, "enabled", true);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------ Happy path

    @Test
    @DisplayName("Geçerli secret + X-User-Name + X-User-Roles → SecurityContext set edilir, chain devam eder")
    void validGatewaySecret_passesAndSetsAuthenticationFromHeaders() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/flights");
        request.addHeader("X-Gateway-Secret", SECRET);
        request.addHeader("X-User-Name", "alice");
        request.addHeader("X-User-Roles", "ROLE_OPERATION_OFFICER");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilterInternal(request, response, filterChain);

        // then
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("alice");
        assertThat(extractRoles(auth)).containsExactly("ROLE_OPERATION_OFFICER");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Virgülle ayrılmış birden çok rol parse edilir")
    void multipleRoles_parsedFromCommaSeparatedHeader() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/flights");
        request.addHeader("X-Gateway-Secret", SECRET);
        request.addHeader("X-User-Name", "alice");
        request.addHeader("X-User-Roles", "ROLE_OPERATION_OFFICER, ROLE_BI_SPECIALIST , ROLE_ADMIN");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilterInternal(request, response, filterChain);

        // then
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(extractRoles(auth))
                .containsExactlyInAnyOrder("ROLE_OPERATION_OFFICER", "ROLE_BI_SPECIALIST", "ROLE_ADMIN");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("X-User-Roles boş ise authorities boş set olur, kullanıcı yine de set edilir")
    void emptyRoles_resultInEmptyAuthorities() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/flights");
        request.addHeader("X-Gateway-Secret", SECRET);
        request.addHeader("X-User-Name", "alice");
        // no roles header
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilterInternal(request, response, filterChain);

        // then
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).isEmpty();
        verify(filterChain).doFilter(request, response);
    }

    // ------------------------------------------------------------------ Reject scenarios

    @Test
    @DisplayName("X-Gateway-Secret yok → 401 ve chain devam etmez")
    void missingGatewaySecret_returns401() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/flights");
        request.addHeader("X-User-Name", "alice");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilterInternal(request, response, filterChain);

        // then
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("X-Gateway-Secret yanlış → 401 ve chain devam etmez")
    void wrongGatewaySecret_returns401() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/flights");
        request.addHeader("X-Gateway-Secret", "wrong-secret");
        request.addHeader("X-User-Name", "alice");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilterInternal(request, response, filterChain);

        // then
        assertThat(response.getStatus()).isEqualTo(401);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("Geçerli secret ama X-User-Name yok → 401")
    void validSecretButMissingUserName_returns401() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/flights");
        request.addHeader("X-Gateway-Secret", SECRET);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilterInternal(request, response, filterChain);

        // then
        assertThat(response.getStatus()).isEqualTo(401);
        verify(filterChain, never()).doFilter(request, response);
    }

    // ------------------------------------------------------------------ shouldNotFilter

    @Test
    @DisplayName("Swagger path /v3/api-docs → shouldNotFilter true")
    void swaggerPath_skipsFilter() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v3/api-docs");

        // when / then
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    @DisplayName("Actuator health → shouldNotFilter true")
    void actuatorHealth_skipsFilter() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    @DisplayName("/swagger-ui/index.html → shouldNotFilter true (wildcard ile eşleşir)")
    void swaggerUiPath_skipsFilter() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/swagger-ui/index.html");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    @DisplayName("/api/flights → korumalı path, shouldNotFilter false")
    void protectedPath_filterApplies() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/flights");
        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    @Test
    @DisplayName("enabled=false → tüm path'ler için shouldNotFilter true (test/dev override)")
    void filterDisabled_skipsAllRequests() {
        // given
        ReflectionTestUtils.setField(filter, "enabled", false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/flights");

        // when / then
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    // ------------------------------------------------------------------ helpers

    private Set<String> extractRoles(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }
}
