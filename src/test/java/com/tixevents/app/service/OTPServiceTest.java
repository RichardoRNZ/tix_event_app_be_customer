package com.tixevents.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import com.tixevents.app.entity.EmailOtp;
import com.tixevents.app.entity.User;
import com.tixevents.app.event.OtpIssuedEvent;
import com.tixevents.app.repository.EmailOtpRepository;
import com.tixevents.app.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("OTPService - Unit Tests")
class OTPServiceTest {

    @Mock private EmailOtpRepository otpRepository;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ApplicationEventPublisher eventPublisher;

    private OTPService otpService;

    @BeforeEach
    void setUp() throws Exception {
        otpService = new OTPService(otpRepository, userRepository, passwordEncoder, eventPublisher);
        setField(otpService, "maxAttempts", 5);
        setField(otpService, "expiryMinutes", 5);
        setField(otpService, "resendCooldownSeconds", 60);
    }

    private void setField(Object target, String name, Object val) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, val);
    }

    private User sampleUser(boolean verified) {
        User u = new User();
        u.setId(1L);
        u.setEmail("test@example.com");
        u.setName("Test User");
        if (verified) {
            u.setVerifiedAt(LocalDateTime.now());
        }
        return u;
    }

    @Nested
    @DisplayName("createOTP")
    class CreateOTPTests {

        @Test
        @DisplayName("Throws 429 TOO_MANY_REQUESTS if last OTP was requested within cooldown period")
        void testCooldownViolated() {
            User u = sampleUser(false);
            EmailOtp lastOtp = new EmailOtp();
            lastOtp.setCreatedDate(LocalDateTime.now().minusSeconds(30)); // 30s ago < 60s cooldown

            when(otpRepository.findTopByUserIdAndUsedAtIsNullOrderByCreatedDateDesc(u.getId()))
                    .thenReturn(Optional.of(lastOtp));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> otpService.createOTP(u));
            assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());
            verify(otpRepository, never()).save(any());
        }

        @Test
        @DisplayName("Creates and saves new OTP, deletes old unused ones, and publishes OtpIssuedEvent")
        void testCreateOTPSuccess() {
            User u = sampleUser(false);
            when(otpRepository.findTopByUserIdAndUsedAtIsNullOrderByCreatedDateDesc(u.getId()))
                    .thenReturn(Optional.empty());
            when(passwordEncoder.encode(any())).thenReturn("hashed-otp");

            otpService.createOTP(u);

            verify(otpRepository).deleteByUserIdAndUsedAtIsNull(u.getId());
            ArgumentCaptor<EmailOtp> captor = ArgumentCaptor.forClass(EmailOtp.class);
            verify(otpRepository).save(captor.capture());

            EmailOtp saved = captor.getValue();
            assertEquals("hashed-otp", saved.getOtpHash());
            assertEquals(u, saved.getUser());
            assertNotNull(saved.getExpiresAt());

            verify(eventPublisher).publishEvent(any(OtpIssuedEvent.class));
        }
    }

    @Nested
    @DisplayName("verifyOTP")
    class VerifyOTPTests {

        @Test
        @DisplayName("Throws 400 if user not found")
        void testUserNotFound() {
            when(userRepository.findByEmail("notfound@example.com")).thenReturn(Optional.empty());

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> 
                otpService.verifyOTP("notfound@example.com", "123456")
            );
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        }

        @Test
        @DisplayName("Throws 400 if user is already verified")
        void testAlreadyVerified() {
            User u = sampleUser(true);
            when(userRepository.findByEmail(u.getEmail())).thenReturn(Optional.of(u));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> 
                otpService.verifyOTP(u.getEmail(), "123456")
            );
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
            assertEquals("Email already verified", ex.getReason());
        }

        @Test
        @DisplayName("Throws 400 if no active OTP exists")
        void testNoActiveOTP() {
            User u = sampleUser(false);
            when(userRepository.findByEmail(u.getEmail())).thenReturn(Optional.of(u));
            when(otpRepository.findTopByUserIdAndUsedAtIsNullOrderByCreatedDateDesc(u.getId()))
                    .thenReturn(Optional.empty());

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> 
                otpService.verifyOTP(u.getEmail(), "123456")
            );
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        }

        @Test
        @DisplayName("Throws 400 if OTP is expired")
        void testOTPExpired() {
            User u = sampleUser(false);
            EmailOtp otp = new EmailOtp();
            otp.setExpiresAt(LocalDateTime.now().minusMinutes(1)); // expired
            otp.setAttempts(0);

            when(userRepository.findByEmail(u.getEmail())).thenReturn(Optional.of(u));
            when(otpRepository.findTopByUserIdAndUsedAtIsNullOrderByCreatedDateDesc(u.getId()))
                    .thenReturn(Optional.of(otp));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> 
                otpService.verifyOTP(u.getEmail(), "123456")
            );
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
            assertEquals("OTP expired", ex.getReason());
        }

        @Test
        @DisplayName("Throws 429 if attempts reached maximum")
        void testMaxAttemptsReached() {
            User u = sampleUser(false);
            EmailOtp otp = new EmailOtp();
            otp.setExpiresAt(LocalDateTime.now().plusMinutes(3));
            otp.setAttempts(5); // max is 5

            when(userRepository.findByEmail(u.getEmail())).thenReturn(Optional.of(u));
            when(otpRepository.findTopByUserIdAndUsedAtIsNullOrderByCreatedDateDesc(u.getId()))
                    .thenReturn(Optional.of(otp));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> 
                otpService.verifyOTP(u.getEmail(), "123456")
            );
            assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());
        }

        @Test
        @DisplayName("Throws 400 and increments attempt count when OTP code does not match")
        void testWrongOTPCode() {
            User u = sampleUser(false);
            EmailOtp otp = new EmailOtp();
            otp.setExpiresAt(LocalDateTime.now().plusMinutes(3));
            otp.setAttempts(1);
            otp.setOtpHash("hash-real");

            when(userRepository.findByEmail(u.getEmail())).thenReturn(Optional.of(u));
            when(otpRepository.findTopByUserIdAndUsedAtIsNullOrderByCreatedDateDesc(u.getId()))
                    .thenReturn(Optional.of(otp));
            when(passwordEncoder.matches("wrong-code", "hash-real")).thenReturn(false);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> 
                otpService.verifyOTP(u.getEmail(), "wrong-code")
            );
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
            assertEquals(2, otp.getAttempts());
        }

        @Test
        @DisplayName("Marks OTP as used and marks user verified when code matches")
        void testVerifySuccess() {
            User u = sampleUser(false);
            EmailOtp otp = new EmailOtp();
            otp.setExpiresAt(LocalDateTime.now().plusMinutes(3));
            otp.setAttempts(0);
            otp.setOtpHash("hash-real");

            when(userRepository.findByEmail(u.getEmail())).thenReturn(Optional.of(u));
            when(otpRepository.findTopByUserIdAndUsedAtIsNullOrderByCreatedDateDesc(u.getId()))
                    .thenReturn(Optional.of(otp));
            when(passwordEncoder.matches("123456", "hash-real")).thenReturn(true);

            otpService.verifyOTP(u.getEmail(), "123456");

            assertNotNull(otp.getUsedAt());
            assertNotNull(u.getVerifiedAt());
        }
    }
}
