package com.podcast.collab.repository;

import com.podcast.collab.entity.Distribution;
import com.podcast.collab.entity.enums.DistributionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DistributionRepository extends JpaRepository<Distribution, Long> {
    List<Distribution> findByEpisodeId(Long episodeId);
    Optional<Distribution> findByEpisodeIdAndPlatformId(Long episodeId, Long platformId);
    long countByEpisodeId(Long episodeId);
    long countByEpisodeIdAndStatus(Long episodeId, DistributionStatus status);
    List<Distribution> findByEpisodeIdIn(Collection<Long> episodeIds);
}
