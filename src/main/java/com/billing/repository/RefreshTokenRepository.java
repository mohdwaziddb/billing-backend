package com.billing.repository;

import com.billing.entity.RefreshToken;
import com.billing.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    void deleteByUser(User user);
    void deleteByUserAndExpiryDateBefore(User user, LocalDateTime date);
}
