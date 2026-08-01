package com.podcast.repository;

import com.podcast.domain.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {
    Optional<Invitation> findByTokenHash(String tokenHash);
    List<Invitation> findByTeamIdOrderByCreatedAtDesc(Long teamId);
}
