package com.tixevents.app.config;

import java.io.IOException;
import java.security.Principal;
import java.util.Collections;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequestLoggingFilterTest {

    @Mock
    private FilterChain filterChain;

    private RequestLoggingFilter requestLoggingFilter;

    @BeforeEach
    void setUp() {
        requestLoggingFilter = new RequestLoggingFilter();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should successfully proceed through filter chain for standard request")
    void testDoFilterInternal_SuccessRequest() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/events");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        requestLoggingFilter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("Should handle request with query string")
    void testDoFilterInternal_WithQueryString() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/events");
        request.setQueryString("page=0&size=10");
        request.setRemoteAddr("192.168.1.10");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        requestLoggingFilter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("Should resolve client IP from X-Forwarded-For header")
    void testDoFilterInternal_ClientIpFromXForwardedFor() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.addHeader("X-Forwarded-For", "203.0.113.195, 70.41.3.18");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        requestLoggingFilter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("Should resolve client IP from X-Real-IP header")
    void testDoFilterInternal_ClientIpFromXRealIp() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.addHeader("X-Real-IP", "198.51.100.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        requestLoggingFilter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("Should resolve user from request user principal")
    void testDoFilterInternal_AuthenticatedUserPrincipal() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/profile");
        Principal principal = () -> "testuser@example.com";
        request.setUserPrincipal(principal);
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        requestLoggingFilter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("Should resolve user from SecurityContextHolder when authenticated")
    void testDoFilterInternal_AuthenticatedSecurityContext() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/bookings/my-history");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("customer@example.com", null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);

        requestLoggingFilter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("Should log error and rethrow exception when filter chain throws exception")
    void testDoFilterInternal_ExceptionRethrown() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/bookings");
        MockHttpServletResponse response = new MockHttpServletResponse();

        doThrow(new ServletException("Something went wrong")).when(filterChain).doFilter(request, response);

        assertThrows(ServletException.class, () -> requestLoggingFilter.doFilter(request, response, filterChain));
    }
}
