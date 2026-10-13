package com.tixevents.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tixevents.app.dto.BookingRequest;
import com.tixevents.app.entity.Booking;
import com.tixevents.app.entity.BookingDetail;
import com.tixevents.app.entity.BookingOutbox;
import com.tixevents.app.entity.User;
import com.tixevents.app.exception.SeatAlreadyBookedException;
import com.tixevents.app.entity.Transaction;
import com.tixevents.app.repository.BookingDetailRepository;
import com.tixevents.app.repository.BookingOutboxRepository;
import com.tixevents.app.repository.BookingRepository;
import com.tixevents.app.repository.TransactionRepository;
import com.tixevents.app.repository.UserRepository;

import org.springframework.data.domain.PageRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.tixevents.app.config.RabbitConfig;
import com.tixevents.app.util.RandomUtil;

@Service
@Slf4j
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingDetailRepository bookingDetailRepository;
    private final BookingOutboxRepository bookingOutboxRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;

    @Transactional
    public Booking createBooking(BookingRequest.CreateBookingRequest request, String userEmail) {
        Optional<Booking> existingBooking = bookingRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existingBooking.isPresent()) {
            return existingBooking.get();
        }

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<Long> activeSeatDetailsIds = bookingDetailRepository.findActiveSeatDetailsIds(request.getSeatDetailsIds());
        if (!activeSeatDetailsIds.isEmpty()) {
            throw new SeatAlreadyBookedException("One or more seats are already booked: " + activeSeatDetailsIds);
        }

        Booking booking = new Booking();
        booking.setUser(user);
        booking.setNoBooking("ORD-" + RandomUtil.generateRandomAlphaNumeric(6).toUpperCase());
        booking.setDate(LocalDateTime.now());
        booking.setStatus("PENDING");
        booking.setUserName(user.getName());
        booking.setEventName(request.getEventName() != null ? request.getEventName() : "Symphony of The Night");
        booking.setIdempotencyKey(request.getIdempotencyKey());
        booking.setExpiresAt(LocalDateTime.now().plusMinutes(10));

        BigDecimal seatPrice = request.getPrice() != null ? request.getPrice() : BigDecimal.valueOf(500000);

        List<BookingDetail> details = new ArrayList<>();
        for (int i = 0; i < request.getSeatDetailsIds().size(); i++) {
            Long seatDetailsId = request.getSeatDetailsIds().get(i);
            BookingDetail detail = new BookingDetail();
            detail.setBooking(booking);
            detail.setSeatDetailsId(seatDetailsId);
            detail.setPrice(seatPrice);
            detail.setIsActive(true);
            if (request.getSeatNumbers() != null && request.getSeatNumbers().size() > i) {
                detail.setSeatNumber(request.getSeatNumbers().get(i));
            } else {
                detail.setSeatNumber("Kursi #" + seatDetailsId);
            }
            details.add(detail);
        }
        booking.setBookingDetails(details);

        Booking savedBooking;
        try {
            savedBooking = bookingRepository.saveAndFlush(booking);
        } catch (DataIntegrityViolationException e) {
            throw new SeatAlreadyBookedException("One or more seats are already booked: " + request.getSeatDetailsIds());
        }

        BookingOutbox outbox = new BookingOutbox();
        outbox.setBooking(savedBooking);
        outbox.setEventType("booking.created");
        outbox.setStatus("PENDING");
        try {
            Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("bookingId", savedBooking.getId());
            payloadMap.put("noBooking", savedBooking.getNoBooking());
            outbox.setPayload(objectMapper.writeValueAsString(payloadMap));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize outbox payload", e);
        }
        bookingOutboxRepository.save(outbox);

        return savedBooking;
    }

    // tidak menggunakan payment gateway
    @Transactional 
    public Booking handlePayment(BookingRequest.PaymentRequest request, Authentication authentication) {
        Booking booking = bookingRepository.findByNoBookingWithLock(request.getNoBooking())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found: " + request.getNoBooking()));

        if (authentication != null) {
            boolean isAdmin = authentication.getAuthorities() != null && authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            if (!booking.getUser().getEmail().equals(authentication.getName()) && !isAdmin) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden access to this booking");
            }
        }
        
        BigDecimal totalPrice = request.getTotalPrice();
        if (totalPrice == null) {
            totalPrice = booking.getBookingDetails() != null
                    ? booking.getBookingDetails().stream()
                            .map(BookingDetail::getPrice)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                    : BigDecimal.ZERO;
        }

        String seatNumbers = booking.getBookingDetails() != null
                ? booking.getBookingDetails().stream()
                        .map(bd -> "Kursi #" + bd.getSeatDetailsId())
                        .collect(Collectors.joining(", "))
                : "-";

        String customerName = booking.getUserName() != null ? booking.getUserName() : booking.getUser().getName();
        String customerEmail = booking.getUser().getEmail();

      
        if ("CONFIRMED".equals(booking.getStatus())) {
            if (Boolean.TRUE.equals(request.getIsPaid())) {
                return booking;
            }
            throw new IllegalStateException("Cannot cancel an already confirmed booking: " + request.getNoBooking());
        }

       
        if ("FAILED".equals(booking.getStatus()) || "EXPIRED".equals(booking.getStatus())) {
            throw new IllegalStateException("Booking cannot be paid because status is " + booking.getStatus() + ": " + request.getNoBooking());
        }

        
        if (booking.getExpiresAt() != null && LocalDateTime.now().isAfter(booking.getExpiresAt())) {
            return expireBooking(booking);
        }

        if (!Boolean.TRUE.equals(request.getIsPaid())) {
            booking.setStatus("FAILED");
            bookingDetailRepository.deactivateByBookingId(booking.getId());
            Booking failedBooking = bookingRepository.save(booking);

            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, "payment.failed",
                    buildPayload("FAILED", booking.getNoBooking(), customerEmail, customerName,
                            booking.getEventName(), seatNumbers, totalPrice, null));

            return failedBooking;
        }

        booking.setStatus("CONFIRMED");
        Booking savedBooking = bookingRepository.save(booking);

        Transaction transaction = new Transaction();
        transaction.setBooking(savedBooking);
        transaction.setBookingNo(savedBooking.getNoBooking());
        transaction.setPaymentMethod(request.getPaymentMethod());
        transaction.setPaymentStatus("SUCCESS");
        transaction.setTotalPrice(totalPrice);
        transaction.setIdempotencyKey(request.getIdempotencyKey());
        Transaction savedTransaction = transactionRepository.save(transaction);

        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, "payment.completed",
                buildPayload("CONFIRMED", savedBooking.getNoBooking(), customerEmail, customerName,
                        savedBooking.getEventName(), seatNumbers, totalPrice, savedTransaction.getId()));

        return savedBooking;
    }

    @Transactional
    public Booking expireBooking(Booking booking) {
        Booking currentBooking = bookingRepository.findByNoBookingWithLock(booking.getNoBooking())
                .orElse(booking);

        if (!"PENDING".equals(currentBooking.getStatus())) {
            log.info("Booking {} already has status {}, skipping expiration", currentBooking.getNoBooking(), currentBooking.getStatus());
            return currentBooking;
        }

        currentBooking.setStatus("EXPIRED");
        bookingDetailRepository.deactivateByBookingId(currentBooking.getId());
        Booking expiredBooking = bookingRepository.save(currentBooking);

        BigDecimal totalPrice = booking.getBookingDetails() != null
                ? booking.getBookingDetails().stream()
                        .map(BookingDetail::getPrice)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                : BigDecimal.ZERO;

        String seatNumbers = booking.getBookingDetails() != null
                ? booking.getBookingDetails().stream()
                        .map(bd -> "Kursi #" + bd.getSeatDetailsId())
                        .collect(Collectors.joining(", "))
                : "-";

        String customerName = booking.getUserName() != null ? booking.getUserName() : (booking.getUser() != null ? booking.getUser().getName() : "Pelanggan");
        String customerEmail = booking.getUser() != null ? booking.getUser().getEmail() : null;

        if (customerEmail != null) {
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, "payment.failed",
                    buildPayload("EXPIRED", booking.getNoBooking(), customerEmail, customerName,
                            booking.getEventName(), seatNumbers, totalPrice, null));
        }

        log.info("Booking {} successfully marked as EXPIRED and seats released", booking.getNoBooking());
        return expiredBooking;
    }

    private Map<String, Object> buildPayload(String status, String noBooking, String customerEmail,
                                              String customerName, String eventName,
                                              String seats, BigDecimal totalPrice, Long transactionId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", status);
        payload.put("noBooking", noBooking);
        payload.put("customerEmail", customerEmail);
        payload.put("customerName", customerName);
        payload.put("eventName", eventName);
        payload.put("seats", seats);
        payload.put("totalPrice", totalPrice);
        if (transactionId != null) {
            payload.put("transactionId", transactionId);
        }
        return payload;
    }

    @Transactional
    public int processExpiredBookings() {
        List<Booking> expiredBookings = bookingRepository.findExpiredBookings(
                "PENDING",
                LocalDateTime.now(),
                PageRequest.of(0, 50)
        );

        if (expiredBookings.isEmpty()) {
            return 0;
        }

        log.info("Found {} expired pending bookings to process", expiredBookings.size());
        int processedCount = 0;
        for (Booking booking : expiredBookings) {
            try {
                expireBooking(booking);
                processedCount++;
            } catch (Exception e) {
                log.error("Failed to expire booking: {}", booking.getNoBooking(), e);
            }
        }
        return processedCount;
    }
}
