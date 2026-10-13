package com.tixevents.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tixevents.app.dto.BookingRequest;
import com.tixevents.app.entity.Booking;
import com.tixevents.app.entity.BookingDetail;
import com.tixevents.app.entity.BookingOutbox;
import com.tixevents.app.entity.Transaction;
import com.tixevents.app.entity.User;
import com.tixevents.app.event.BookingNotificationEvent;
import com.tixevents.app.exception.SeatAlreadyBookedException;
import com.tixevents.app.repository.BookingDetailRepository;
import com.tixevents.app.repository.BookingOutboxRepository;
import com.tixevents.app.repository.BookingRepository;
import com.tixevents.app.repository.TransactionRepository;
import com.tixevents.app.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookingService - Unit Tests")
class BookingServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private BookingDetailRepository bookingDetailRepository;
    @Mock private BookingOutboxRepository bookingOutboxRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private ObjectMapper objectMapper = new ObjectMapper();
    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(
                bookingRepository,
                bookingDetailRepository,
                bookingOutboxRepository,
                transactionRepository,
                userRepository,
                objectMapper,
                eventPublisher
        );
    }

    private User sampleUser() {
        User user = new User();
        user.setId(1L);
        user.setName("John Doe");
        user.setEmail("john@example.com");
        return user;
    }

    private Booking sampleBooking(String status, LocalDateTime expiresAt) {
        Booking booking = new Booking();
        booking.setId(10L);
        booking.setUser(sampleUser());
        booking.setNoBooking("BKG-123456");
        booking.setStatus(status);
        booking.setExpiresAt(expiresAt);
        booking.setEventName("Symphony of The Night");

        BookingDetail detail1 = new BookingDetail();
        detail1.setId(101L);
        detail1.setSeatDetailsId(501L);
        detail1.setSeatNumber("Kursi #501");
        detail1.setPrice(BigDecimal.valueOf(500000));
        detail1.setIsActive(true);

        booking.setBookingDetails(new ArrayList<>(List.of(detail1)));
        return booking;
    }

    @Nested
    @DisplayName("createBooking")
    class CreateBookingTests {

        @Test
        @DisplayName("Returns existing booking if idempotency key matches")
        void testIdempotentBooking() {
            BookingRequest.CreateBookingRequest req = new BookingRequest.CreateBookingRequest();
            req.setIdempotencyKey("idem-key-1");

            Booking existing = sampleBooking("PENDING", LocalDateTime.now().plusMinutes(10));
            when(bookingRepository.findByIdempotencyKey("idem-key-1")).thenReturn(Optional.of(existing));

            Booking result = bookingService.createBooking(req, "john@example.com");

            assertEquals(existing, result);
            verify(bookingRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Throws IllegalArgumentException if user not found")
        void testUserNotFound() {
            BookingRequest.CreateBookingRequest req = new BookingRequest.CreateBookingRequest();
            req.setIdempotencyKey("idem-key-2");
            when(bookingRepository.findByIdempotencyKey("idem-key-2")).thenReturn(Optional.empty());
            when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> 
                bookingService.createBooking(req, "unknown@example.com")
            );
        }

        @Test
        @DisplayName("Throws SeatAlreadyBookedException if seats are already active")
        void testSeatsAlreadyBooked() {
            BookingRequest.CreateBookingRequest req = new BookingRequest.CreateBookingRequest();
            req.setIdempotencyKey("idem-key-3");
            req.setSeatDetailsIds(List.of(501L, 502L));

            when(bookingRepository.findByIdempotencyKey("idem-key-3")).thenReturn(Optional.empty());
            when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(sampleUser()));
            when(bookingDetailRepository.findActiveSeatDetailsIds(List.of(501L, 502L))).thenReturn(List.of(501L));

            assertThrows(SeatAlreadyBookedException.class, () ->
                bookingService.createBooking(req, "john@example.com")
            );
        }

        @Test
        @DisplayName("Successfully creates booking, details, and outbox")
        void testCreateBookingSuccess() {
            BookingRequest.CreateBookingRequest req = new BookingRequest.CreateBookingRequest();
            req.setIdempotencyKey("idem-key-4");
            req.setSeatDetailsIds(List.of(501L));
            req.setSeatNumbers(List.of("VIP-1"));
            req.setPrice(BigDecimal.valueOf(750000));
            req.setEventName("Jazz Festival");

            when(bookingRepository.findByIdempotencyKey("idem-key-4")).thenReturn(Optional.empty());
            when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(sampleUser()));
            when(bookingDetailRepository.findActiveSeatDetailsIds(List.of(501L))).thenReturn(Collections.emptyList());
            when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(i -> {
                Booking b = i.getArgument(0);
                b.setId(100L);
                return b;
            });

            Booking created = bookingService.createBooking(req, "john@example.com");

            assertNotNull(created);
            assertEquals("PENDING", created.getStatus());
            assertEquals("Jazz Festival", created.getEventName());
            assertEquals(1, created.getBookingDetails().size());
            assertEquals(BigDecimal.valueOf(750000), created.getBookingDetails().get(0).getPrice());
            assertEquals("VIP-1", created.getBookingDetails().get(0).getSeatNumber());

            verify(bookingOutboxRepository).save(any(BookingOutbox.class));
        }
    }

    @Nested
    @DisplayName("handlePayment")
    class HandlePaymentTests {

        @Test
        @DisplayName("Throws ResponseStatusException (404) if booking not found")
        void testBookingNotFound() {
            BookingRequest.PaymentRequest req = new BookingRequest.PaymentRequest();
            req.setNoBooking("BKG-NONEXIST");
            when(bookingRepository.findByNoBookingWithLock("BKG-NONEXIST")).thenReturn(Optional.empty());

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> bookingService.handlePayment(req, null));
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        }

        @Test
        @DisplayName("Returns existing booking if already CONFIRMED and isPaid is true (Idempotent)")
        void testAlreadyConfirmedIdempotent() {
            BookingRequest.PaymentRequest req = new BookingRequest.PaymentRequest();
            req.setNoBooking("BKG-123456");
            req.setIsPaid(true);

            Booking confirmed = sampleBooking("CONFIRMED", LocalDateTime.now().plusMinutes(5));
            when(bookingRepository.findByNoBookingWithLock("BKG-123456")).thenReturn(Optional.of(confirmed));

            Booking result = bookingService.handlePayment(req, null);
            assertEquals("CONFIRMED", result.getStatus());
            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Throws IllegalStateException if trying to cancel already CONFIRMED booking")
        void testCancelConfirmedBookingThrows() {
            BookingRequest.PaymentRequest req = new BookingRequest.PaymentRequest();
            req.setNoBooking("BKG-123456");
            req.setIsPaid(false);

            Booking confirmed = sampleBooking("CONFIRMED", LocalDateTime.now().plusMinutes(5));
            when(bookingRepository.findByNoBookingWithLock("BKG-123456")).thenReturn(Optional.of(confirmed));

            assertThrows(IllegalStateException.class, () -> bookingService.handlePayment(req, null));
        }

        @Test
        @DisplayName("Throws IllegalStateException if status is already FAILED or EXPIRED")
        void testAlreadyFailedOrExpiredThrows() {
            BookingRequest.PaymentRequest req = new BookingRequest.PaymentRequest();
            req.setNoBooking("BKG-123456");
            req.setIsPaid(true);

            Booking failed = sampleBooking("FAILED", LocalDateTime.now().minusMinutes(1));
            when(bookingRepository.findByNoBookingWithLock("BKG-123456")).thenReturn(Optional.of(failed));

            assertThrows(IllegalStateException.class, () -> bookingService.handlePayment(req, null));
        }

        @Test
        @DisplayName("Expires booking if expiresAt has passed when paying")
        void testPaymentExpiredTriggersExpireBooking() {
            BookingRequest.PaymentRequest req = new BookingRequest.PaymentRequest();
            req.setNoBooking("BKG-123456");
            req.setIsPaid(true);

            Booking expired = sampleBooking("PENDING", LocalDateTime.now().minusMinutes(5));
            when(bookingRepository.findByNoBookingWithLock("BKG-123456")).thenReturn(Optional.of(expired));
            when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

            Booking result = bookingService.handlePayment(req, null);

            assertEquals("EXPIRED", result.getStatus());
            verify(bookingDetailRepository).deactivateByBookingId(expired.getId());
            verify(eventPublisher).publishEvent(any(BookingNotificationEvent.class));
        }

        @Test
        @DisplayName("Payment failed / cancelled: sets FAILED and deactivates seats")
        void testPaymentCancelled() {
            BookingRequest.PaymentRequest req = new BookingRequest.PaymentRequest();
            req.setNoBooking("BKG-123456");
            req.setIsPaid(false);
            req.setTotalPrice(BigDecimal.valueOf(500000));

            Booking pending = sampleBooking("PENDING", LocalDateTime.now().plusMinutes(5));
            when(bookingRepository.findByNoBookingWithLock("BKG-123456")).thenReturn(Optional.of(pending));
            when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

            Booking result = bookingService.handlePayment(req, null);

            assertEquals("FAILED", result.getStatus());
            verify(bookingDetailRepository).deactivateByBookingId(pending.getId());
            verify(eventPublisher).publishEvent(any(BookingNotificationEvent.class));
            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Payment success: sets CONFIRMED, creates outbox and transaction")
        void testPaymentSuccess() {
            BookingRequest.PaymentRequest req = new BookingRequest.PaymentRequest();
            req.setNoBooking("BKG-123456");
            req.setIsPaid(true);
            req.setPaymentMethod("BCA_VA");
            req.setIdempotencyKey("idem-tx-1");
            req.setTotalPrice(BigDecimal.valueOf(500000));

            Booking pending = sampleBooking("PENDING", LocalDateTime.now().plusMinutes(5));
            when(bookingRepository.findByNoBookingWithLock("BKG-123456")).thenReturn(Optional.of(pending));
            when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));
            when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));

            Booking result = bookingService.handlePayment(req, null);

            assertEquals("CONFIRMED", result.getStatus());
            verify(bookingOutboxRepository).save(any(BookingOutbox.class));
            verify(transactionRepository).save(any(Transaction.class));

            ArgumentCaptor<BookingNotificationEvent> captor = ArgumentCaptor.forClass(BookingNotificationEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertEquals("SUCCESS", captor.getValue().getStatus());
            assertEquals("john@example.com", captor.getValue().getEmail());
        }
    }

    @Nested
    @DisplayName("expireBooking and processExpiredBookings")
    class ExpirationTests {

        @Test
        @DisplayName("expireBooking skips if booking is not PENDING")
        void testExpireBookingSkipsNonPending() {
            Booking confirmed = sampleBooking("CONFIRMED", LocalDateTime.now().minusMinutes(5));
            when(bookingRepository.findByNoBookingWithLock("BKG-123456")).thenReturn(Optional.of(confirmed));

            Booking result = bookingService.expireBooking(confirmed);

            assertEquals("CONFIRMED", result.getStatus());
            verify(bookingDetailRepository, never()).deactivateByBookingId(any());
        }

        @Test
        @DisplayName("processExpiredBookings processes batch of expired bookings")
        void testProcessExpiredBookings() {
            Booking b1 = sampleBooking("PENDING", LocalDateTime.now().minusMinutes(5));
            Booking b2 = sampleBooking("PENDING", LocalDateTime.now().minusMinutes(10));
            b2.setNoBooking("BKG-789012");

            when(bookingRepository.findExpiredBookings(eq("PENDING"), any(LocalDateTime.class), any(Pageable.class)))
                    .thenReturn(List.of(b1, b2));
            when(bookingRepository.findByNoBookingWithLock(b1.getNoBooking())).thenReturn(Optional.of(b1));
            when(bookingRepository.findByNoBookingWithLock(b2.getNoBooking())).thenReturn(Optional.of(b2));
            when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

            int count = bookingService.processExpiredBookings();

            assertEquals(2, count);
            assertEquals("EXPIRED", b1.getStatus());
            assertEquals("EXPIRED", b2.getStatus());
        }
    }
}
