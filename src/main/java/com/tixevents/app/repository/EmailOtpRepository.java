package com.tixevents.app.repository;

import com.tixevents.app.entity.EmailOtp;
import com.tixevents.app.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmailOtpRepository extends JpaRepository<EmailOtp, Long> {

    // Mengambil OTP terakhir yang di-generate untuk user tertentu dan belum digunakan
  Optional<EmailOtp> findTopByUserIdAndUsedAtIsNullOrderByCreatedDateDesc(Long userId);
    void deleteByUserIdAndUsedAtIsNull(Long userId);
    
}
