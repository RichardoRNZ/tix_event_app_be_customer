package com.tixevents.app.job;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.tixevents.app.service.BookingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingExpirationJob {

    private final BookingService bookingService;

    /**
     * Job berkala untuk memeriksa dan membatalkan booking yang telah melewati batas waktu (expired).
     * Dijalankan setiap 15 detik (dapat diatur via app.booking.expiration-check-interval-ms).
     * Berkat partial index pada DB, eksekusi query berlangsung instan (< 2ms).
     */
    @Scheduled(fixedDelayString = "${app.booking.expiration-check-interval-ms:15000}", initialDelay = 10000)
    public void processExpiredBookings() {
        try {
            int expiredCount = bookingService.processExpiredBookings();
            if (expiredCount > 0) {
                log.info("BookingExpirationJob processed and expired {} booking(s)", expiredCount);
            }
        } catch (Exception e) {
            log.error("Error occurred while running BookingExpirationJob", e);
        }
    }
}
