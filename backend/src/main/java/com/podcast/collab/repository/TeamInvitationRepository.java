package com.podcast.collab.repository;

import com.podcast.collab.entity.TeamInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TeamInvitationRepository extends JpaRepository<TeamInvitation, Long> {
    Optional<TeamInvitation> findByToken(String token);
    List<TeamInvitation> findByTeamId(Long teamId);
    List<TeamInvitation> findByEmailAndAcceptedAtIsNull(String email);
    void deleteByExpiresAtBefore(LocalDateTime now);
}
