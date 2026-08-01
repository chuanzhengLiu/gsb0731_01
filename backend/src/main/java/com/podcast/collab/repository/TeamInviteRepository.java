package com.podcast.collab.repository;

import com.podcast.collab.entity.TeamInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TeamInviteRepository extends JpaRepository<TeamInvite, Long> {
    Optional<TeamInvite> findByToken(String token);
}
