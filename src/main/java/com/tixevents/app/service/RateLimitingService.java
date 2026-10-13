package com.tixevents.app.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitingService {

    @Value("${app.ratelimit.login.ip.capacity:100}")
    private int loginIpCapacity;

    @Value("${app.ratelimit.login.ip.duration-minutes:5}")
    private int loginIpDurationMinutes;

    @Value("${app.ratelimit.resend-otp.ip.capacity:10}")
    private int resendOtpIpCapacity;

    @Value("${app.ratelimit.resend-otp.ip.duration-minutes:1}")
    private int resendOtpIpDurationMinutes;

    @Value("${app.ratelimit.resend-otp.user.capacity:1}")
    private int resendOtpUserCapacity;

    @Value("${app.ratelimit.resend-otp.user.duration-seconds:30}")
    private int resendOtpUserDurationSeconds;
    
    private final Map<String, Bucket> loginIpBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> resendOtpIpBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> resendOtpUserBuckets = new ConcurrentHashMap<>();

    public Bucket resolveLoginIpBucket(String ip) {
        return loginIpBuckets.computeIfAbsent(ip, this::newLoginIpBucket);
    }
    
    public Bucket resolveResendOtpIpBucket(String ip) {
        return resendOtpIpBuckets.computeIfAbsent(ip, this::newResendOtpIpBucket);
    }

    public Bucket resolveResendOtpUserBucket(String email) {
        return resendOtpUserBuckets.computeIfAbsent(email, this::newResendOtpUserBucket);
    }

    private Bucket newLoginIpBucket(String ip) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(loginIpCapacity)
                .refillIntervally(loginIpCapacity, Duration.ofMinutes(loginIpDurationMinutes))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket newResendOtpIpBucket(String ip) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(resendOtpIpCapacity)
                .refillIntervally(resendOtpIpCapacity, Duration.ofMinutes(resendOtpIpDurationMinutes))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket newResendOtpUserBucket(String email) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(resendOtpUserCapacity)
                .refillIntervally(resendOtpUserCapacity, Duration.ofSeconds(resendOtpUserDurationSeconds))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
