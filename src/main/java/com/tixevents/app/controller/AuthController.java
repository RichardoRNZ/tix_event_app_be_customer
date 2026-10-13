package com.tixevents.app.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;

import com.tixevents.app.dto.AuthDTO;
import com.tixevents.app.dto.UserDTO;
import com.tixevents.app.service.AuthService;
import com.tixevents.app.service.OTPService;
import com.tixevents.app.service.RateLimitingService;
import com.tixevents.app.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final OTPService otpService;
    private final RateLimitingService rateLimitingService;
    private final UserRepository userRepository;

    @PostMapping("/login")
    public ResponseEntity<AuthDTO.LoginResponse> login(@Valid @RequestBody AuthDTO.LoginRequest request, HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr();
        log.info("Login request received for email: {} | IP: {}", request.getEmail(), ip);
        if (!rateLimitingService.resolveLoginIpBucket(ip).tryConsume(1)) {
            log.warn("Login rate limit exceeded for IP: {}", ip);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        AuthDTO.LoginResponse response = authService.login(request);
        log.info("Login successful for email: {}", request.getEmail());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        log.info("Logout request received | IP: {}", request.getRemoteAddr());
        authService.logout(request);
        log.info("Logout successful | IP: {}", request.getRemoteAddr());
        return ResponseEntity.ok(Map.of("message", "Logout successful"));
    }

    @PostMapping("/register")
    public ResponseEntity<UserDTO> register(@Valid @RequestBody AuthDTO.RegisterRequest request) {
        log.info("Register request received for email: {}", request.getEmail());
        UserDTO user = authService.register(request);
        log.info("Register successful for email: {}", request.getEmail());
        return ResponseEntity.ok(user);
    }

    @GetMapping("/profile")
    public ResponseEntity<UserDTO> getCurrentUser() {
        log.info("Get current user profile request received");
        UserDTO user = authService.getCurrentUser();
        log.info("Profile retrieved for email: {}", user.getEmail());
        return ResponseEntity.ok(user);
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Void> verify(@RequestBody @Valid AuthDTO.VerifyOtpRequest req) {
        log.info("Verify email (OTP) request received for email: {}", req.getEmail());
        otpService.verifyOTP(req.getEmail(), req.getOtp());
        log.info("Email verified successfully for email: {}", req.getEmail());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<Void> resend(@RequestBody @Valid AuthDTO.ResendOtpRequest req, HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr();
        String email = req.getEmail();
        log.info("Resend OTP request received for email: {} | IP: {}", email, ip);

        if (!rateLimitingService.resolveResendOtpIpBucket(ip).tryConsume(1)) {
            log.warn("Resend OTP rate limit exceeded for IP: {}", ip);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        if (!rateLimitingService.resolveResendOtpUserBucket(email).tryConsume(1)) {
            log.warn("Resend OTP rate limit exceeded for email: {}", email);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }

        userRepository.findByEmail(email)
            .filter(u -> u.getVerifiedAt() == null)
            .ifPresent(otpService::createOTP);
        log.info("Resend OTP processed for email: {}", email);
        return ResponseEntity.ok().build();
    }
}
