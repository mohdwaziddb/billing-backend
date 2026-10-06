package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NotificationChannelRepository extends JpaRepository<NotificationChannel, Long> {
    @Query("select case when count(e)>0 then true else false end from NotificationChannel e where (:company is null or 1=1) and lower(e.channelName)=lower(:channelName)")
    boolean existsByCompanyAndChannelNameIgnoreCase(@Param("company") Company company, @Param("channelName") String channelName);

    @Query("select e from NotificationChannel e where (:company is null or 1=1) and e.active=true order by e.defaultChannel desc, e.channelName asc")
    List<NotificationChannel> findByCompanyAndActiveTrueOrderByDefaultChannelDescChannelNameAsc(@Param("company") Company company);

    @Query("select e from NotificationChannel e where (:company is null or 1=1) and lower(e.channelName)=lower(:channelName)")
    Optional<NotificationChannel> findByCompanyAndChannelNameIgnoreCase(@Param("company") Company company, @Param("channelName") String channelName);
}
