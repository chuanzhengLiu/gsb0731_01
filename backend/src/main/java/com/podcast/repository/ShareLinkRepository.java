package com.podcast.repository;

import com.podcast.domain.ShareLink;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ShareLinkRepository extends JpaRepository<ShareLink, Long> {
    Optional<ShareLink> findByTokenHash(String tokenHash);
    List<ShareLink> findByEpisodeIdOrderByCreatedAtDesc(Long episodeId);
    List<ShareLink> findByTeamIdOrderByCreatedAtDesc(Long teamId);
}
