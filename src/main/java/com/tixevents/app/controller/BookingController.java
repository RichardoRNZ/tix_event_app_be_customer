package com.tixevents.app.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;

import com.tixevents.app.dto.BookingDTO;
import com.tixevents.app.dto.BookingRequest;
import com.tixevents.app.entity.Booking;
import com.tixevents.app.entity.User;
import com.tixevents.app.mapper.BookingMapper;
import com.tixevents.app.repository.UserRepository;
import com.tixevents.app.service.BookingService;
import com.tixevents.app.service.query.BookingQueryService;

@Slf4j
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {
    
    private final BookingService service;
    private final BookingQueryService queryService;
    private final BookingMapper mapper;
    private final UserRepository userRepository;

    @PostMapping
    public ResponseEntity<BookingDTO> createBooking(@Valid @RequestBody BookingRequest.CreateBookingRequest request, Authentication authentication) {
        String userEmail = authentication.getName();
        log.info("Create booking request received for user: {}", userEmail);
        Booking booking = service.createBooking(request, userEmail);
        log.info("Booking created successfully: {} for user: {}", booking.getNoBooking(), userEmail);
        return new ResponseEntity<>(mapper.toDto(booking), HttpStatus.CREATED);
    }

    @PostMapping("/payment")
    public ResponseEntity<BookingDTO> handlePayment(@Valid @RequestBody BookingRequest.PaymentRequest request, Authentication authentication) {
        log.info("Payment request received for user: {}", authentication.getName());
        Booking booking = service.handlePayment(request, authentication);
        log.info("Payment processed for booking: {} | user: {}", booking.getNoBooking(), authentication.getName());
        return ResponseEntity.ok(mapper.toDto(booking));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingDTO> getBooking(@PathVariable Long id, Authentication authentication) {
        String userEmail = authentication.getName();
        log.info("Get booking request received for id: {} | user: {}", id, userEmail);
        Booking booking = queryService.findById(id);

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!booking.getUser().getEmail().equals(userEmail) && !isAdmin) {
            log.warn("Access denied to booking id: {} for user: {}", id, userEmail);
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        return ResponseEntity.ok(mapper.toDto(booking));
    }

 
    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<BookingDTO>> getUserBookingHistory(
            @PathVariable Long userId,
            @RequestParam(required = false) String noBooking,
            @PageableDefault(sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        log.info("Get user booking history request received for userId: {} | requester: {}", userId, authentication.getName());
        User currentUser = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        boolean isAdmin = Boolean.TRUE.equals(currentUser.getIsAdmin());
        if (!currentUser.getId().equals(userId) && !isAdmin) {
            log.warn("Access denied to booking history of userId: {} for requester: {}", userId, authentication.getName());
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        return ResponseEntity.ok(queryService.getUserBookingHistory(userId, noBooking, pageable));
    }

   
    @GetMapping("/my-history")
    public ResponseEntity<Page<BookingDTO>> getMyBookingHistory(
            @RequestParam(required = false) String noBooking,
            @PageableDefault(sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        log.info("Get my booking history request received for user: {}", authentication.getName());
        User currentUser = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return ResponseEntity.ok(queryService.getUserBookingHistory(currentUser.getId(), noBooking, pageable));
    }

    @GetMapping({"/admin", "/booking/all"})
    public ResponseEntity<Page<BookingDTO>> getAllBookingsForAdmin(
            @RequestParam(required = false) String noBooking,
            @PageableDefault(sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        log.info("Get all bookings (admin) request received by: {}", authentication.getName());
        User currentUser = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!Boolean.TRUE.equals(currentUser.getIsAdmin())) {
            log.warn("Non-admin user attempted to access all bookings: {}", authentication.getName());
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        return ResponseEntity.ok(queryService.getAllBookingsForAdmin(noBooking, pageable));
    }
}
