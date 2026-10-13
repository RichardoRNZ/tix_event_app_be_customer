package com.tixevents.app.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.tixevents.app.entity.EmailOtp;
import com.tixevents.app.entity.User;
import com.tixevents.app.event.OtpIssuedEvent;
import com.tixevents.app.repository.EmailOtpRepository;
import com.tixevents.app.repository.UserRepository;

import org.springframework.transaction.annotation.Transactional;

import com.tixevents.app.util.RandomUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor 
public class OTPService {

    private static final SecureRandom RANDOM = new SecureRandom();
    @Value("${app.otp.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.otp.expiry-minutes:5}")
    private int expiryMinutes;

    @Value("${app.otp.resend-cooldown-seconds:60}")
    private int resendCooldownSeconds;

    private final EmailOtpRepository otpRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void createOTP(User user) {
        otpRepository.findTopByUserIdAndUsedAtIsNullOrderByCreatedDateDesc(user.getId())
            .ifPresent(last -> {
                if (last.getCreatedDate().isAfter(LocalDateTime.now().minusSeconds(resendCooldownSeconds))) {
                    throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Please wait to request another OTP");
                }
            });

        otpRepository.deleteByUserIdAndUsedAtIsNull(user.getId());
        String plainOtp = RandomUtil.generateRandomCode(6);
        EmailOtp otp = new EmailOtp();
        otp.setUser(user);
        otp.setOtpHash(passwordEncoder.encode(plainOtp));
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(expiryMinutes));
        otp.setCreatedDate(LocalDateTime.now());
        otpRepository.save(otp);

        eventPublisher.publishEvent(new OtpIssuedEvent(user.getEmail(), plainOtp));
    } 

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public void verifyOTP(String email, String code) {
         User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP not valid"));

        if (user.getVerifiedAt() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email already verified");
        }

        EmailOtp otp = otpRepository.findTopByUserIdAndUsedAtIsNullOrderByCreatedDateDesc(user.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP not valid"));

        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP expired");
        }
        if (otp.getAttempts() >= maxAttempts) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts, please request new OTP");
        }

        if (!passwordEncoder.matches(code, otp.getOtpHash())) {
            otp.setAttempts(otp.getAttempts() + 1);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP not valid");
        }

        otp.setUsedAt(LocalDateTime.now());
        user.setVerifiedAt(LocalDateTime.now());
    }

}
