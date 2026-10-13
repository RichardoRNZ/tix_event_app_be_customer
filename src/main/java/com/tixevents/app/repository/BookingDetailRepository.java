package com.tixevents.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.tixevents.app.entity.BookingDetail;
import java.util.List;

@Repository
public interface BookingDetailRepository extends JpaRepository<BookingDetail, Long> {

    @Query("SELECT COUNT(bd) > 0 FROM BookingDetail bd WHERE bd.seatDetailsId = :seatDetailsId AND bd.isActive = true")
    boolean existsActiveBySeatDetailsId(@Param("seatDetailsId") Long seatDetailsId);

    @Query("SELECT bd.seatDetailsId FROM BookingDetail bd WHERE bd.seatDetailsId IN :seatDetailsIds AND bd.isActive = true")
    List<Long> findActiveSeatDetailsIds(@Param("seatDetailsIds") List<Long> seatDetailsIds);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE booking_details SET is_active = false WHERE booking_id = :bookingId", nativeQuery = true)
    int deactivateByBookingId(@Param("bookingId") Long bookingId);
}
