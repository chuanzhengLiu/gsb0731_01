package com.podcast.repository;

import com.podcast.domain.AudioVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AudioVersionRepository extends JpaRepository<AudioVersion, Long> {
    List<AudioVersion> findByEpisodeIdOrderByVersionNumberDesc(Long episodeId);
    Optional<AudioVersion> findTopByEpisodeIdOrderByVersionNumberDesc(Long episodeId);
    long countByEpisodeId(Long episodeId);
    List<AudioVersion> findByEpisodeIdIn(List<Long> episodeIds);
}
