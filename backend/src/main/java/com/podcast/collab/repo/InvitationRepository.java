package com.podcast.collab.repo;

import com.podcast.collab.domain.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {
    Optional<Invitation> findByToken(String token);
    List<Invitation> findByTeamIdOrderByCreatedAtDesc(Long teamId);
}
