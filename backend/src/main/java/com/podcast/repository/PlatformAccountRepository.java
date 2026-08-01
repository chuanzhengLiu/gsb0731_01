package com.podcast.repository;

import com.podcast.domain.PlatformAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PlatformAccountRepository extends JpaRepository<PlatformAccount, Long> {
    List<PlatformAccount> findByTeamId(Long teamId);
    Optional<PlatformAccount> findByTeamIdAndPlatformId(Long teamId, Long platformId);
}
