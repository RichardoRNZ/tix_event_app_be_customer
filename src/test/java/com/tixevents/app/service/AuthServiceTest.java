package com.tixevents.app.service;

import com.tixevents.app.entity.User;
import com.tixevents.app.entity.UserToken;
import com.tixevents.app.repository.UserRepository;
import com.tixevents.app.repository.UserTokenRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserTokenRepository userTokenRepository;

    @Mock
    private JWTService jwtService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("logout should delete token from repository and clear security context")
    void testLogoutSuccess() {
        String testToken = "valid.test.jwt.token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + testToken);

        User user = new User();
        user.setEmail("user@example.com");

        var auth = new UsernamePasswordAuthenticationToken("user@example.com", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        authService.logout(request);

        verify(userTokenRepository).deleteByToken(testToken);
        verify(userTokenRepository).deleteByUser(user);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("logout without token header should still delete by authenticated user and clear context")
    void testLogoutWithoutHeader() {
        when(request.getHeader("Authorization")).thenReturn(null);

        User user = new User();
        user.setEmail("user@example.com");

        var auth = new UsernamePasswordAuthenticationToken("user@example.com", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        authService.logout(request);

        verify(userTokenRepository, never()).deleteByToken(anyString());
        verify(userTokenRepository).deleteByUser(user);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("cleanupExpiredTokens should find and delete only expired tokens")
    void testCleanupExpiredTokens() {
        UserToken expiredToken1 = new UserToken();
        expiredToken1.setToken("token.expired.1");

        UserToken validToken = new UserToken();
        validToken.setToken("token.valid.2");

        UserToken expiredToken2 = new UserToken();
        expiredToken2.setToken("token.expired.3");

        when(userTokenRepository.findAll()).thenReturn(List.of(expiredToken1, validToken, expiredToken2));
        when(jwtService.isTokenExpired("token.expired.1")).thenReturn(true);
        when(jwtService.isTokenExpired("token.valid.2")).thenReturn(false);
        when(jwtService.isTokenExpired("token.expired.3")).thenReturn(true);

        int count = authService.cleanupExpiredTokens();

        assertEquals(2, count);
        verify(userTokenRepository).deleteAll(List.of(expiredToken1, expiredToken2));
    }
}
