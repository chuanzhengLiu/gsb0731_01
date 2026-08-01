package com.podcast.repository;

import com.podcast.domain.Distribution;
import com.podcast.domain.DistributionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DistributionRepository extends JpaRepository<Distribution, Long> {
    List<Distribution> findByEpisodeId(Long episodeId);
    Optional<Distribution> findByEpisodeIdAndPlatformId(Long episodeId, Long platformId);
    List<Distribution> findByStatus(DistributionStatus status);
    List<Distribution> findByEpisodeIdIn(List<Long> episodeIds);
}
