package com.tixevents.app.controller;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.tixevents.app.entity.Booking;
import com.tixevents.app.entity.BookingDetail;
import com.tixevents.app.entity.User;
import com.tixevents.app.repository.UserRepository;
import com.tixevents.app.repository.UserTokenRepository;
import com.tixevents.app.service.AuthService;
import com.tixevents.app.service.BookingService;
import com.tixevents.app.service.JWTService;
import com.tixevents.app.service.OTPService;
import com.tixevents.app.service.RateLimitingService;
import com.tixevents.app.service.UserService;
import com.tixevents.app.service.query.BookingQueryService;
import com.tixevents.app.service.query.UserQueryService;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;

/**
 * Base class for web-layer tests. Each concrete subclass is a {@code @WebMvcTest}
 * that loads the real controller, the real {@code SecurityConfig}, the real
 * {@code JWTAuthFilter} and the real {@code GlobalExceptionHandler}, while all
 * services / repositories are mocked.
 */
abstract class AbstractControllerTest {

    /** Tag for tests that intentionally document an existing bug (they are expected to FAIL until fixed). */
    static final String KNOWN_BUG = "known-bug";

    @Autowired
    protected MockMvc mockMvc;

    // ---- security / filter dependencies ----
    @MockitoBean protected JWTService jwtService;
    @MockitoBean protected UserTokenRepository userTokenRepository;
    @MockitoBean protected UserRepository userRepository;

    // ---- controller dependencies ----
    @MockitoBean protected AuthService authService;
    @MockitoBean protected OTPService otpService;
    @MockitoBean protected RateLimitingService rateLimitingService;
    @MockitoBean protected BookingService bookingService;
    @MockitoBean protected BookingQueryService bookingQueryService;
    @MockitoBean protected UserService userService;
    @MockitoBean protected UserQueryService userQueryService;

    // ---- helpers ----
    protected static Bucket bucket(int capacity) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(capacity).refillIntervally(capacity, Duration.ofMinutes(1)).build())
                .build();
    }

    protected static Bucket exhaustedBucket() {
        Bucket b = bucket(1);
        b.tryConsume(1);
        return b;
    }

    protected static User userEntity(Long id, String email, boolean admin) {
        User u = new User();
        u.setId(id);
        u.setName("User " + id);
        u.setEmail(email);
        u.setPassword("hashed");
        u.setIsAdmin(admin);
        u.setBlocked(false);
        u.setVerifiedAt(LocalDateTime.now());
        return u;
    }

    protected static Booking booking(Long id, User owner, String status) {
        Booking b = new Booking();
        b.setId(id);
        b.setUser(owner);
        b.setNoBooking("BKG-ABC" + id);
        b.setStatus(status);
        b.setDate(LocalDateTime.now());
        b.setUserName(owner.getName());
        b.setEventName("Symphony of The Night");
        BookingDetail d = new BookingDetail();
        d.setId(id * 10);
        d.setSeatDetailsId(100L + id);
        d.setSeatNumber("A" + id);
        d.setPrice(BigDecimal.valueOf(500000));
        // NOTE: detail.booking is intentionally NOT set back to avoid Lombok @Data recursion (see BUG report)
        b.setBookingDetails(List.of(d));
        return b;
    }
}
