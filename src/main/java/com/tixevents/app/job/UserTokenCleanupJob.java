package com.tixevents.app.job;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.tixevents.app.service.AuthService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserTokenCleanupJob {

    private final AuthService authService;

    /**
     * Job berkala untuk membersihkan token yang sudah expired dari database (auto logout).
     * Dijalankan setiap 60 detik secara default (dapat diatur via app.jwt.cleanup-interval-ms).
     */
    @Scheduled(fixedDelayString = "${app.jwt.cleanup-interval-ms:60000}", initialDelay = 15000)
    public void cleanupExpiredTokens() {
        try {
            int cleanedCount = authService.cleanupExpiredTokens();
            if (cleanedCount > 0) {
                log.info("UserTokenCleanupJob automatically logged out and cleaned {} expired token(s)", cleanedCount);
            }
        } catch (Exception e) {
            log.error("Error occurred while running UserTokenCleanupJob", e);
        }
    }
}
