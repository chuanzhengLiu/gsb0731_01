package com.podcast.collab.repo;

import com.podcast.collab.domain.PlatformAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlatformAccountRepository extends JpaRepository<PlatformAccount, Long> {
    List<PlatformAccount> findByTeamIdOrderByCreatedAtDesc(Long teamId);
}
