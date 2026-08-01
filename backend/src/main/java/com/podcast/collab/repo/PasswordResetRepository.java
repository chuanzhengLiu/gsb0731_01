package com.podcast.collab.repo;

import com.podcast.collab.domain.PasswordReset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetRepository extends JpaRepository<PasswordReset, Long> {
    Optional<PasswordReset> findByTokenHash(String tokenHash);
    @Modifying
    @Query("delete from PasswordReset p where p.expiresAt < :cutoff")
    int deleteExpired(Instant cutoff);
}
