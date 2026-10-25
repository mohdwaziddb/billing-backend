package com.billing.repository;

import com.billing.entity.User;
import com.billing.entity.UserSession;
import com.billing.entity.enums.UserSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    Optional<UserSession> findBySessionKey(String sessionKey);

    List<UserSession> findByUserOrderByLastActiveAtDesc(User user);

    List<UserSession> findByUserAndStatus(User user, UserSessionStatus status);

    /** Khatam hue purane sessions (history) saaf karne ke liye. */
    @Modifying
    @Query("delete from UserSession s where s.user = :user and s.status <> com.billing.entity.enums.UserSessionStatus.ACTIVE and s.endedAt < :before")
    int deleteEndedBefore(@Param("user") User user, @Param("before") LocalDateTime before);
}
