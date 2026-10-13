package com.tixevents.app.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.server.ResponseStatusException;

import com.tixevents.app.config.SecurityConfig;
import com.tixevents.app.dto.AuthDTO;
import com.tixevents.app.dto.UserDTO;
import com.tixevents.app.entity.User;
import com.tixevents.app.mapper.BookingDetailMapperImpl;
import com.tixevents.app.mapper.BookingMapperImpl;
import com.tixevents.app.mapper.UserMapperImpl;

@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, UserMapperImpl.class, BookingMapperImpl.class, BookingDetailMapperImpl.class})
@DisplayName("AuthController - /api/auth/**")
class AuthControllerTest extends AbstractControllerTest {

    private static final String LOGIN_JSON = "{\"email\":\"user@x.com\",\"password\":\"secret\"}";

    private static UserDTO userDto(String email) {
        UserDTO dto = new UserDTO();
        dto.setId(1L);
        dto.setEmail(email);
        dto.setName("User");
        return dto;
    }

    // =====================================================================
    @Nested
    @DisplayName("POST /api/auth/login")
    class Login {

        @Test
        @DisplayName("200 - returns token when credentials valid")
        void loginSuccess() throws Exception {
            when(rateLimitingService.resolveLoginIpBucket(anyString())).thenReturn(bucket(5));
            when(authService.login(any())).thenReturn(new AuthDTO.LoginResponse("jwt-token", userDto("user@x.com"), "Login successful"));

            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value("jwt-token"))
                    .andExpect(jsonPath("$.user.email").value("user@x.com"))
                    .andExpect(jsonPath("$.message").value("Login successful"));
        }

        @Test
        @DisplayName("429 - IP rate limit exceeded, service not called")
        void loginRateLimited() throws Exception {
            when(rateLimitingService.resolveLoginIpBucket(anyString())).thenReturn(exhaustedBucket());

            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_JSON))
                    .andExpect(status().isTooManyRequests());
            verify(authService, never()).login(any());
        }

        @Test
        @DisplayName("401 - bad credentials")
        void loginBadCredentials() throws Exception {
            when(rateLimitingService.resolveLoginIpBucket(anyString())).thenReturn(bucket(5));
            when(authService.login(any())).thenThrow(new BadCredentialsException("bad"));

            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_JSON))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Invalid credentials"));
        }

        @Test
        @DisplayName("401 - user not verified")
        void loginNotVerified() throws Exception {
            when(rateLimitingService.resolveLoginIpBucket(anyString())).thenReturn(bucket(5));
            when(authService.login(any())).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not verified"));

            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_JSON))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("User not verified"));
        }

        @Test
        @DisplayName("409 - user already logged in on another device")
        void loginActiveSession() throws Exception {
            when(rateLimitingService.resolveLoginIpBucket(anyString())).thenReturn(bucket(5));
            when(authService.login(any())).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "User login on another device"));

            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_JSON))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("[BUG-07] malformed JSON should return 400, not 500")
        void loginMalformedJson() throws Exception {
            when(rateLimitingService.resolveLoginIpBucket(anyString())).thenReturn(bucket(5));

            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{not-json"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("[BUG-09] empty body should be rejected with 400 (no @Valid / constraints on LoginRequest)")
        void loginEmptyBodyShouldBeValidated() throws Exception {
            when(rateLimitingService.resolveLoginIpBucket(anyString())).thenReturn(bucket(5));

            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
            verify(authService, never()).login(any());
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("POST /api/auth/logout")
    class Logout {

        @Test
        @DisplayName("200 - public endpoint, delegates to AuthService")
        void logoutPublic() throws Exception {
            mockMvc.perform(post("/api/auth/logout"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Logout successful"));
            verify(authService).logout(any());
        }

        @Test
        @DisplayName("[BUG-16] logout with malformed token should proceed on public endpoint, but JWTAuthFilter getServletPath() mismatch causes 401")
        void logoutWithInvalidToken() throws Exception {
            when(jwtService.extractUsername("garbage")).thenThrow(new io.jsonwebtoken.MalformedJwtException("bad"));

            mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer garbage"))
                    .andExpect(status().isOk());
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("POST /api/auth/register")
    class Register {

        @Test
        @DisplayName("200 - registers new user")
        void registerSuccess() throws Exception {
            when(authService.register(any())).thenReturn(userDto("new@x.com"));

            mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"New\",\"email\":\"new@x.com\",\"password\":\"Secret123\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("new@x.com"))
                    .andExpect(jsonPath("$.password").doesNotExist());
        }

        @Test
        @DisplayName("400 - email already registered")
        void registerDuplicate() throws Exception {
            when(authService.register(any())).thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email already registered"));

            mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"New\",\"email\":\"new@x.com\",\"password\":\"Secret123\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Email already registered"));
        }

        @Test
        @DisplayName("[BUG-09] invalid payload (blank name, bad email, blank password) should return 400")
        void registerInvalidPayload() throws Exception {
            mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"\",\"email\":\"not-an-email\",\"password\":\"\"}"))
                    .andExpect(status().isBadRequest());
            verify(authService, never()).register(any());
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("GET /api/auth/profile")
    class Profile {

        @Test
        @DisplayName("401 - unauthenticated returns JSON from entry point")
        void profileUnauthenticated() throws Exception {
            mockMvc.perform(get("/api/auth/profile"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Authentication required"));
        }

        @Test
        @DisplayName("200 - authenticated returns current user")
        void profileAuthenticated() throws Exception {
            when(authService.getCurrentUser()).thenReturn(userDto("user@x.com"));

            mockMvc.perform(get("/api/auth/profile").with(user("user@x.com").roles("USER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("user@x.com"));
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("POST /api/auth/verify-email")
    class VerifyEmail {

        @Test
        @DisplayName("200 - valid OTP")
        void verifySuccess() throws Exception {
            mockMvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"user@x.com\",\"otp\":\"123456\"}"))
                    .andExpect(status().isOk());
            verify(otpService).verifyOTP("user@x.com", "123456");
        }

        @Test
        @DisplayName("400 - missing otp / invalid email (bean validation)")
        void verifyInvalidBody() throws Exception {
            mockMvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"not-email\"}"))
                    .andExpect(status().isBadRequest());
            verify(otpService, never()).verifyOTP(anyString(), anyString());
        }

        @Test
        @DisplayName("400 - wrong OTP")
        void verifyWrongOtp() throws Exception {
            doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP not valid"))
                    .when(otpService).verifyOTP("user@x.com", "000000");

            mockMvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"user@x.com\",\"otp\":\"000000\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("OTP not valid"));
        }

        @Test
        @DisplayName("429 - too many attempts")
        void verifyTooManyAttempts() throws Exception {
            doThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts, please request new OTP"))
                    .when(otpService).verifyOTP(anyString(), anyString());

            mockMvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"user@x.com\",\"otp\":\"000000\"}"))
                    .andExpect(status().isTooManyRequests());
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("POST /api/auth/resend-otp")
    class ResendOtp {

        private static final String BODY = "{\"email\":\"user@x.com\"}";

        @Test
        @DisplayName("200 - unverified user gets new OTP")
        void resendForUnverified() throws Exception {
            User u = userEntity(1L, "user@x.com", false);
            u.setVerifiedAt(null);
            when(rateLimitingService.resolveResendOtpIpBucket(anyString())).thenReturn(bucket(5));
            when(rateLimitingService.resolveResendOtpUserBucket("user@x.com")).thenReturn(bucket(5));
            when(userRepository.findByEmail("user@x.com")).thenReturn(Optional.of(u));

            mockMvc.perform(post("/api/auth/resend-otp").contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isOk());
            verify(otpService).createOTP(u);
        }

        @Test
        @DisplayName("200 - already verified user: no OTP sent (no enumeration)")
        void resendForVerified() throws Exception {
            when(rateLimitingService.resolveResendOtpIpBucket(anyString())).thenReturn(bucket(5));
            when(rateLimitingService.resolveResendOtpUserBucket("user@x.com")).thenReturn(bucket(5));
            when(userRepository.findByEmail("user@x.com")).thenReturn(Optional.of(userEntity(1L, "user@x.com", false)));

            mockMvc.perform(post("/api/auth/resend-otp").contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isOk());
            verify(otpService, never()).createOTP(any());
        }

        @Test
        @DisplayName("200 - unknown email: no OTP sent (no enumeration)")
        void resendForUnknown() throws Exception {
            when(rateLimitingService.resolveResendOtpIpBucket(anyString())).thenReturn(bucket(5));
            when(rateLimitingService.resolveResendOtpUserBucket("user@x.com")).thenReturn(bucket(5));
            when(userRepository.findByEmail("user@x.com")).thenReturn(Optional.empty());

            mockMvc.perform(post("/api/auth/resend-otp").contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isOk());
            verify(otpService, never()).createOTP(any());
        }

        @Test
        @DisplayName("429 - IP bucket exhausted")
        void resendIpLimited() throws Exception {
            when(rateLimitingService.resolveResendOtpIpBucket(anyString())).thenReturn(exhaustedBucket());

            mockMvc.perform(post("/api/auth/resend-otp").contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isTooManyRequests());
            verify(otpService, never()).createOTP(any());
        }

        @Test
        @DisplayName("429 - per-email bucket exhausted")
        void resendUserLimited() throws Exception {
            when(rateLimitingService.resolveResendOtpIpBucket(anyString())).thenReturn(bucket(5));
            when(rateLimitingService.resolveResendOtpUserBucket(eq("user@x.com"))).thenReturn(exhaustedBucket());

            mockMvc.perform(post("/api/auth/resend-otp").contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isTooManyRequests());
            verify(otpService, never()).createOTP(any());
        }

        @Test
        @DisplayName("400 - invalid email format")
        void resendInvalidEmail() throws Exception {
            mockMvc.perform(post("/api/auth/resend-otp").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"bad\"}"))
                    .andExpect(status().isBadRequest());
        }
    }
}
