package com.tixevents.app.config;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.tixevents.app.service.JWTService;
import com.tixevents.app.repository.UserTokenRepository;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component 
@RequiredArgsConstructor
@Slf4j
public class JWTAuthFilter extends OncePerRequestFilter {
    private final JWTService jwtService;
    private final UserDetailsService userDetailsService;
    private final UserTokenRepository userTokenRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(7).trim();
        if (token.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        try {
            String username = jwtService.extractUsername(token);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails user = userDetailsService.loadUserByUsername(username);
                if (jwtService.isValid(token, user)) {
                    boolean isTokenValid = userTokenRepository.findByToken(token).isPresent();
                    if (isTokenValid) {
                        var auth = new UsernamePasswordAuthenticationToken(
                                user, null, user.getAuthorities());
                        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    } else {
                        // Token valid JWT tetapi sudah dihapus dari user_tokens (user sudah logout)
                        if (!isPublicEndpoint(request)) {
                            SecurityContextHolder.clearContext();
                            writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "Session has expired or logged out. Please log in again.");
                            return;
                        }
                    }
                }
            }
        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired: auto logging out token");
            // Auto logout: hapus token expired dari database
            try {
                userTokenRepository.deleteByToken(token);
            } catch (Exception ex) {
                log.error("Failed to delete expired token from repository", ex);
            }
            SecurityContextHolder.clearContext();

            if (isPublicEndpoint(request)) {
                chain.doFilter(request, response);
                return;
            }

            writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "TOKEN_EXPIRED", "Token has expired. You have been automatically logged out.");
            return;
        } catch (JwtException | IllegalArgumentException e) {
            SecurityContextHolder.clearContext();
            if (isPublicEndpoint(request)) {
                chain.doFilter(request, response);
                return;
            }
            writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "Invalid JWT Token");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isPublicEndpoint(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path == null || path.isEmpty()) {
            path = request.getRequestURI();
        }
        if (path != null && path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path != null && (
               path.equals("/api/auth/login") ||
               path.equals("/api/auth/register") ||
               path.equals("/api/auth/verify-email") ||
               path.equals("/api/auth/resend-otp") ||
               path.equals("/api/auth/logout")
        );
    }

    private void writeErrorResponse(HttpServletResponse response, int status, String error, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(String.format("{\"status\":%d,\"error\":\"%s\",\"message\":\"%s\"}", status, error, message));
    }
}

