package com.billing.repository;

import com.billing.entity.PasswordResetOtp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PasswordResetOtpRepository extends JpaRepository<PasswordResetOtp, Long> {
    List<PasswordResetOtp> findByUserIdOrderByIdDesc(Long userId);

    void deleteByUserId(Long userId);
}
