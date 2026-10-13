package com.tixevents.app.config;

import java.io.IOException;
import java.security.Principal;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Filter to log every incoming HTTP request and outgoing HTTP response,
 * capturing HTTP method, URI, query parameters, client IP, response status,
 * user authentication, and request execution duration.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        long startTime = System.currentTimeMillis();
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        String clientIp = resolveClientIp(request);
        String fullPath = (queryString != null && !queryString.isBlank()) ? uri + "?" + queryString : uri;

        if (log.isDebugEnabled()) {
            log.debug("--> [HTTP REQUEST] {} {} | Client IP: {}", method, fullPath, clientIp);
        }

        try {
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("<-- [HTTP ERROR] {} {} | Client IP: {} | Duration: {}ms | Error: {}",
                    method, fullPath, clientIp, duration, ex.getMessage());
            throw ex;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            int status = response.getStatus();
            String user = resolveUser(request);

            if (log.isDebugEnabled()) {
                log.debug("<-- [HTTP RESPONSE] {} {} | Status: {} | Duration: {}ms | User: {}",
                        method, fullPath, status, duration, user);
            } else if (log.isInfoEnabled()) {
                log.info("[HTTP] {} {} | Status: {} | Duration: {}ms | User: {}",
                        method, fullPath, status, duration, user);
            }
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }

    private String resolveUser(HttpServletRequest request) {
        Principal principal = request.getUserPrincipal();
        if (principal != null && principal.getName() != null && !principal.getName().isBlank()) {
            return principal.getName();
        }
        if (request.getRemoteUser() != null && !request.getRemoteUser().isBlank()) {
            return request.getRemoteUser();
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getName() != null && !"anonymousUser".equals(auth.getName())) {
            return auth.getName();
        }
        return "anonymous";
    }
}
