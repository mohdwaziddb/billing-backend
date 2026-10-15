package com.billing.repository;

import com.billing.entity.UserMfaDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserMfaDeviceRepository extends JpaRepository<UserMfaDevice, Long> {

    List<UserMfaDevice> findByUserIdOrderByIdAsc(Long userId);

    List<UserMfaDevice> findByUserIdAndEnabledTrue(Long userId);

    List<UserMfaDevice> findByUserIdAndEnabledFalse(Long userId);

    Optional<UserMfaDevice> findFirstByUserIdAndDeviceNameIgnoreCaseOrderByIdDesc(Long userId, String deviceName);

    void deleteByUserId(Long userId);
}
