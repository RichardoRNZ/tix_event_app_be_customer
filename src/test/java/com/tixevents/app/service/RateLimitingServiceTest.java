package com.tixevents.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.bucket4j.Bucket;

@DisplayName("RateLimitingService - Unit Tests")
class RateLimitingServiceTest {

    private RateLimitingService rateLimitingService;

    @BeforeEach
    void setUp() throws Exception {
        rateLimitingService = new RateLimitingService();
        setField(rateLimitingService, "loginIpCapacity", 2);
        setField(rateLimitingService, "loginIpDurationMinutes", 1);
        setField(rateLimitingService, "resendOtpIpCapacity", 2);
        setField(rateLimitingService, "resendOtpIpDurationMinutes", 1);
        setField(rateLimitingService, "resendOtpUserCapacity", 1);
        setField(rateLimitingService, "resendOtpUserDurationSeconds", 30);
    }

    private void setField(Object target, String name, Object val) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, val);
    }

    @Test
    @DisplayName("resolveLoginIpBucket caches bucket per IP and enforces capacity")
    void testLoginIpBucket() {
        Bucket b1 = rateLimitingService.resolveLoginIpBucket("127.0.0.1");
        Bucket b2 = rateLimitingService.resolveLoginIpBucket("127.0.0.1");
        assertSame(b1, b2, "Same IP should resolve to cached bucket instance");

        assertTrue(b1.tryConsume(1));
        assertTrue(b1.tryConsume(1));
        assertFalse(b1.tryConsume(1), "Capacity is 2, third attempt must be blocked");

        Bucket otherIpBucket = rateLimitingService.resolveLoginIpBucket("192.168.1.1");
        assertTrue(otherIpBucket.tryConsume(1), "Different IP should have separate capacity");
    }

    @Test
    @DisplayName("resolveResendOtpUserBucket caches bucket per user email and enforces 1 request capacity")
    void testResendOtpUserBucket() {
        Bucket b1 = rateLimitingService.resolveResendOtpUserBucket("alice@example.com");
        Bucket b2 = rateLimitingService.resolveResendOtpUserBucket("alice@example.com");
        assertSame(b1, b2);

        assertTrue(b1.tryConsume(1));
        assertFalse(b1.tryConsume(1), "User capacity is 1, second attempt within window must be rejected");
    }
}
