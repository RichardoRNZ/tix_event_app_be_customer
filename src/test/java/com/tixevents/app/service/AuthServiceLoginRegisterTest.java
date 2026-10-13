package com.tixevents.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import com.tixevents.app.dto.AuthDTO;
import com.tixevents.app.dto.UserDTO;
import com.tixevents.app.entity.User;
import com.tixevents.app.entity.UserToken;
import com.tixevents.app.mapper.UserMapperImpl;
import com.tixevents.app.repository.UserRepository;
import com.tixevents.app.repository.UserTokenRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService - login / register / getCurrentUser")
class AuthServiceLoginRegisterTest {

    static final String KNOWN_BUG = "known-bug";

    @Mock UserRepository userRepository;
    @Mock JWTService jwtService;
    @Mock AuthenticationManager authenticationManager;
    @Mock UserTokenRepository userTokenRepository;
    @Mock OTPService otpService;

    PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, new UserMapperImpl(), passwordEncoder, jwtService,
                authenticationManager, userTokenRepository, otpService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static User verifiedUser() {
        User u = new User();
        u.setId(1L);
        u.setName("User");
        u.setEmail("user@x.com");
        u.setPassword("hash");
        u.setIsAdmin(false);
        u.setBlocked(false);
        u.setVerifiedAt(LocalDateTime.now());
        return u;
    }

    private static AuthDTO.LoginRequest loginReq() {
        AuthDTO.LoginRequest r = new AuthDTO.LoginRequest();
        r.setEmail("user@x.com");
        r.setPassword("secret");
        return r;
    }

    private static AuthDTO.RegisterRequest registerReq(String name, String email, String password) {
        AuthDTO.RegisterRequest r = new AuthDTO.RegisterRequest();
        r.setName(name);
        r.setEmail(email);
        r.setPassword(password);
        return r;
    }

    // =====================================================================
    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("success: replaces old token, stores new token, returns DTO")
        void success() {
            User u = verifiedUser();
            when(authenticationManager.authenticate(any())).thenReturn(new UsernamePasswordAuthenticationToken("user@x.com", null, List.of()));
            when(userRepository.findByEmail("user@x.com")).thenReturn(Optional.of(u));
            when(userTokenRepository.findByUser(u)).thenReturn(Optional.empty());
            when(jwtService.generateToken(any())).thenReturn("jwt");

            AuthDTO.LoginResponse res = authService.login(loginReq());

            assertEquals("jwt", res.getToken());
            assertEquals("user@x.com", res.getUser().getEmail());
            verify(userTokenRepository).deleteByUser(u);
            ArgumentCaptor<UserToken> captor = ArgumentCaptor.forClass(UserToken.class);
            verify(userTokenRepository).save(captor.capture());
            assertEquals("jwt", captor.getValue().getToken());
        }

        @Test
        @DisplayName("bad credentials increment failedLoginCount and throw BadCredentialsException")
        void badCredentials() {
            User u = verifiedUser();
            when(userRepository.findByEmail("user@x.com")).thenReturn(Optional.of(u));
            when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

            assertThrows(BadCredentialsException.class, () -> authService.login(loginReq()));
            assertEquals(1, u.getFailedLoginCount());
            assertEquals(false, u.getBlocked());
            verify(userRepository).save(u);
            verify(userTokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("more than 3 failed attempts blocks the user and sets blockedTime")
        void failedLoginLocksUserAfterThreeAttempts() {
            User u = verifiedUser();
            u.setFailedLoginCount(3);
            when(userRepository.findByEmail("user@x.com")).thenReturn(Optional.of(u));
            when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

            assertThrows(BadCredentialsException.class, () -> authService.login(loginReq()));
            assertEquals(4, u.getFailedLoginCount());
            assertEquals(true, u.getBlocked());
            assertNotNull(u.getBlockedTime());
            verify(userRepository).save(u);
        }

        @Test
        @DisplayName("401 when user not verified")
        void notVerified() {
            User u = verifiedUser();
            u.setVerifiedAt(null);
            when(userRepository.findByEmail("user@x.com")).thenReturn(Optional.of(u));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> authService.login(loginReq()));
            assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        }

        @Test
        @DisplayName("409 when an un-expired token already exists (single session)")
        void activeSession() {
            User u = verifiedUser();
            UserToken t = new UserToken();
            t.setToken("old");
            when(authenticationManager.authenticate(any())).thenReturn(null);
            when(userRepository.findByEmail("user@x.com")).thenReturn(Optional.of(u));
            when(userTokenRepository.findByUser(u)).thenReturn(Optional.of(t));
            when(jwtService.isTokenExpired("old")).thenReturn(false);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> authService.login(loginReq()));
            assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
            verify(userTokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("login allowed when previous token is expired")
        void expiredPreviousToken() {
            User u = verifiedUser();
            UserToken t = new UserToken();
            t.setToken("old");
            when(authenticationManager.authenticate(any())).thenReturn(null);
            when(userRepository.findByEmail("user@x.com")).thenReturn(Optional.of(u));
            when(userTokenRepository.findByUser(u)).thenReturn(Optional.of(t));
            when(jwtService.isTokenExpired("old")).thenReturn(true);
            when(jwtService.generateToken(any())).thenReturn("new");

            assertEquals("new", authService.login(loginReq()).getToken());
        }

        @Test
        @DisplayName("blocked user cannot log in if blockedTime is < 30 minutes from now (403 FORBIDDEN)")
        void blockedUserCannotLoginWithin30Minutes() {
            User u = verifiedUser();
            u.setBlocked(true);
            u.setBlockedTime(LocalDateTime.now().minusMinutes(10));
            when(userRepository.findByEmail("user@x.com")).thenReturn(Optional.of(u));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> authService.login(loginReq()));
            assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
            verify(authenticationManager, never()).authenticate(any());
        }

        @Test
        @DisplayName("blocked user with null blockedTime cannot log in (403 FORBIDDEN)")
        void blockedUserWithNullBlockedTimeCannotLogin() {
            User u = verifiedUser();
            u.setBlocked(true);
            u.setBlockedTime(null);
            when(userRepository.findByEmail("user@x.com")).thenReturn(Optional.of(u));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> authService.login(loginReq()));
            assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
            verify(authenticationManager, never()).authenticate(any());
        }

        @Test
        @DisplayName("blocked user can log in when >= 30 minutes pass, and login success resets blocked and failedLoginCount")
        void blockedUserCanLoginAfter30MinutesAndResetsBlock() {
            User u = verifiedUser();
            u.setBlocked(true);
            u.setBlockedTime(LocalDateTime.now().minusMinutes(35));
            u.setFailedLoginCount(4);

            when(userRepository.findByEmail("user@x.com")).thenReturn(Optional.of(u));
            when(authenticationManager.authenticate(any())).thenReturn(new UsernamePasswordAuthenticationToken("user@x.com", null, List.of()));
            when(userTokenRepository.findByUser(u)).thenReturn(Optional.empty());
            when(jwtService.generateToken(any())).thenReturn("token123");

            AuthDTO.LoginResponse res = authService.login(loginReq());

            assertEquals("token123", res.getToken());
            assertEquals(false, u.getBlocked());
            assertNull(u.getBlockedTime());
            assertEquals(0, u.getFailedLoginCount());
            verify(userRepository).save(u);
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("new user: saved with BCrypt password, not admin, not blocked, unverified; OTP issued")
        void newUser() {
            when(userRepository.findByEmail("new@x.com")).thenReturn(Optional.empty());
            when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(5L);
                return u;
            });

            UserDTO dto = authService.register(registerReq("New", "new@x.com", "Secret123"));

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).saveAndFlush(captor.capture());
            User saved = captor.getValue();
            assertEquals(Boolean.FALSE, saved.getIsAdmin());
            assertEquals(Boolean.FALSE, saved.getBlocked());
            assertNull(saved.getVerifiedAt());
            assertEquals(true, passwordEncoder.matches("Secret123", saved.getPassword()));
            verify(otpService).createOTP(saved);
            assertEquals(5L, dto.getId());
        }

        @Test
        @DisplayName("400 when email already registered & verified")
        void verifiedDuplicate() {
            when(userRepository.findByEmail("user@x.com")).thenReturn(Optional.of(verifiedUser()));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> authService.register(registerReq("X", "user@x.com", "p")));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
            verify(otpService, never()).createOTP(any());
        }

        @Test
        @DisplayName("existing unverified user: re-issues OTP, does not create new user")
        void unverifiedExisting() {
            User u = verifiedUser();
            u.setVerifiedAt(null);
            when(userRepository.findByEmail("user@x.com")).thenReturn(Optional.of(u));

            authService.register(registerReq("X", "user@x.com", "p"));

            verify(otpService).createOTP(u);
            verify(userRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("race condition on unique email -> 400")
        void dataIntegrityViolation() {
            when(userRepository.findByEmail("new@x.com")).thenReturn(Optional.empty());
            when(userRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> authService.register(registerReq("New", "new@x.com", "p")));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        }

        @Test
        @DisplayName("[BUG-09] null password should be rejected with 400, not crash with IllegalArgumentException (500)")
        void nullPassword() {
            when(userRepository.findByEmail("new@x.com")).thenReturn(Optional.empty());

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> authService.register(registerReq("New", "new@x.com", null)));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("getCurrentUser")
    class CurrentUser {

        @Test
        @DisplayName("401 when no authentication")
        void noAuth() {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> authService.getCurrentUser());
            assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        }

        @Test
        @DisplayName("returns DTO for authenticated user")
        void found() {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("user@x.com", null, List.of()));
            when(userRepository.findByEmail("user@x.com")).thenReturn(Optional.of(verifiedUser()));

            assertEquals("user@x.com", authService.getCurrentUser().getEmail());
        }

        @Test
        @DisplayName("404 when authenticated user no longer exists")
        void notFound() {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("ghost@x.com", null, List.of()));
            when(userRepository.findByEmail("ghost@x.com")).thenReturn(Optional.empty());

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> authService.getCurrentUser());
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        }
    }
}
