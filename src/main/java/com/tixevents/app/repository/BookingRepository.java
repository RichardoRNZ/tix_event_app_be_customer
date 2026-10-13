package com.tixevents.app.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import com.tixevents.app.entity.Booking;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findByIdempotencyKey(String idempotencyKey);
    Optional<Booking> findByNoBooking(String noBooking);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.noBooking = :noBooking")
    Optional<Booking> findByNoBookingWithLock(@Param("noBooking") String noBooking);

    @Query("SELECT b FROM Booking b WHERE b.status = :status AND b.expiresAt <= :now")
    List<Booking> findExpiredBookings(@Param("status") String status, @Param("now") LocalDateTime now, Pageable pageable);

    @EntityGraph(attributePaths = {"bookingDetails", "user"})
    @Query(
        value = "SELECT b FROM Booking b WHERE b.user.id = :userId AND (:noBooking IS NULL OR :noBooking = '' OR LOWER(b.noBooking) LIKE LOWER(CONCAT('%', :noBooking, '%')))",
        countQuery = "SELECT COUNT(b) FROM Booking b WHERE b.user.id = :userId AND (:noBooking IS NULL OR :noBooking = '' OR LOWER(b.noBooking) LIKE LOWER(CONCAT('%', :noBooking, '%')))"
    )
    Page<Booking> findByUserIdAndNoBooking(@Param("userId") Long userId, @Param("noBooking") String noBooking, Pageable pageable);

    @EntityGraph(attributePaths = {"bookingDetails", "user"})
    @Query(
        value = "SELECT b FROM Booking b WHERE (:noBooking IS NULL OR :noBooking = '' OR LOWER(b.noBooking) LIKE LOWER(CONCAT('%', :noBooking, '%')))",
        countQuery = "SELECT COUNT(b) FROM Booking b WHERE (:noBooking IS NULL OR :noBooking = '' OR LOWER(b.noBooking) LIKE LOWER(CONCAT('%', :noBooking, '%')))"
    )
    Page<Booking> findAllByNoBooking(@Param("noBooking") String noBooking, Pageable pageable);
}
