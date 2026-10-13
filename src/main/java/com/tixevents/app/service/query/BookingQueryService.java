package com.tixevents.app.service.query;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tixevents.app.dto.BookingDTO;
import com.tixevents.app.entity.Booking;
import com.tixevents.app.mapper.BookingMapper;
import com.tixevents.app.repository.BookingRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingQueryService {

    private final BookingRepository repository;
    private final BookingMapper mapper;

    public Booking findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found with id: " + id));
    }

    /**
     * Mengambil riwayat booking untuk user tertentu (Gambar 1) dengan pencarian noBooking dan pagination
     */
    public Page<BookingDTO> getUserBookingHistory(Long userId, String noBooking, Pageable pageable) {
        String searchParam = (noBooking != null && !noBooking.trim().isEmpty()) ? noBooking.trim() : null;
        Page<Booking> bookingPage = repository.findByUserIdAndNoBooking(userId, searchParam, pageable);
        return bookingPage.map(mapper::toDto);
    }

    /**
     * Mengambil semua data pesanan untuk panel admin (Gambar 2) dengan pencarian noBooking dan pagination
     */
    public Page<BookingDTO> getAllBookingsForAdmin(String noBooking, Pageable pageable) {
        String searchParam = (noBooking != null && !noBooking.trim().isEmpty()) ? noBooking.trim() : null;
        Page<Booking> bookingPage = repository.findAllByNoBooking(searchParam, pageable);
        return bookingPage.map(mapper::toDto);
    }
}
