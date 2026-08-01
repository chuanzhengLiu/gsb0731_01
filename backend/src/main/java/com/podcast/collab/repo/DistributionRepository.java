package com.podcast.collab.repo;

import com.podcast.collab.domain.Distribution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DistributionRepository extends JpaRepository<Distribution, Long> {
    List<Distribution> findByEpisodeIdOrderByCreatedAtDesc(Long episodeId);
    Optional<Distribution> findByEpisodeIdAndPlatformAccountId(Long episodeId, Long accountId);
}
