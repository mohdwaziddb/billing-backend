package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationChannelRepository extends JpaRepository<NotificationChannel, Long> {
    boolean existsByCompanyAndChannelNameIgnoreCase(Company company, String channelName);
    List<NotificationChannel> findByCompanyAndActiveTrueOrderByDefaultChannelDescChannelNameAsc(Company company);
    Optional<NotificationChannel> findByCompanyAndChannelNameIgnoreCase(Company company, String channelName);
}
