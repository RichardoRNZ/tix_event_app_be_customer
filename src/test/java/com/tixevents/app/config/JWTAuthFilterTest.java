package com.tixevents.app.config;

import com.tixevents.app.repository.UserTokenRepository;
import com.tixevents.app.service.JWTService;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JWTAuthFilterTest {

    @Mock
    private JWTService jwtService;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private UserTokenRepository userTokenRepository;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JWTAuthFilter jwtAuthFilter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should auto logout and return 401 TOKEN_EXPIRED when token is expired")
    void testExpiredTokenAutoLogout() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/bookings");
        request.addHeader("Authorization", "Bearer expired.token.jwt");

        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.extractUsername("expired.token.jwt"))
                .thenThrow(new ExpiredJwtException(null, null, "JWT expired"));

        jwtAuthFilter.doFilter(request, response, filterChain);

        // Verify token deleted from DB (auto-logout)
        verify(userTokenRepository).deleteByToken("expired.token.jwt");
        // Verify response is 401 with TOKEN_EXPIRED
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("TOKEN_EXPIRED"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("Should return 401 when token is valid JWT but revoked/logged out from repository")
    void testRevokedTokenReturns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/bookings");
        request.addHeader("Authorization", "Bearer loggedout.token.jwt");

        MockHttpServletResponse response = new MockHttpServletResponse();

        var userDetails = new User("user@example.com", "pass", List.of());
        when(jwtService.extractUsername("loggedout.token.jwt")).thenReturn("user@example.com");
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(userDetails);
        when(jwtService.isValid("loggedout.token.jwt", userDetails)).thenReturn(true);
        when(userTokenRepository.findByToken("loggedout.token.jwt")).thenReturn(Optional.empty());

        jwtAuthFilter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Session has expired or logged out"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("Should authenticate successfully when token is valid and present in repository")
    void testValidTokenAuthenticates() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/bookings");
        request.addHeader("Authorization", "Bearer valid.token.jwt");

        MockHttpServletResponse response = new MockHttpServletResponse();

        var userDetails = new User("user@example.com", "pass", List.of());
        when(jwtService.extractUsername("valid.token.jwt")).thenReturn("user@example.com");
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(userDetails);
        when(jwtService.isValid("valid.token.jwt", userDetails)).thenReturn(true);
        when(userTokenRepository.findByToken("valid.token.jwt")).thenReturn(Optional.of(new com.tixevents.app.entity.UserToken()));

        jwtAuthFilter.doFilter(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("user@example.com", SecurityContextHolder.getContext().getAuthentication().getName());
        verify(filterChain).doFilter(request, response);
    }
}
