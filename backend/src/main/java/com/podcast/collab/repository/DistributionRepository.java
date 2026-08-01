package com.podcast.collab.repository;

import com.podcast.collab.entity.Distribution;
import com.podcast.collab.entity.DistributionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DistributionRepository extends JpaRepository<Distribution, Long> {
    List<Distribution> findByEpisodeId(Long episodeId);
    Optional<Distribution> findByEpisodeIdAndPlatformId(Long episodeId, Long platformId);
    List<Distribution> findByScheduledAtBetween(LocalDateTime from, LocalDateTime to);
    long countByStatus(DistributionStatus status);
    List<Distribution> findByStatus(DistributionStatus status);
}
