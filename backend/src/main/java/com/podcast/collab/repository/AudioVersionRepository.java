package com.podcast.collab.repository;

import com.podcast.collab.entity.AudioVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AudioVersionRepository extends JpaRepository<AudioVersion, Long> {
    List<AudioVersion> findByEpisodeIdOrderByVersionNumberDesc(Long episodeId);
    List<AudioVersion> findByEpisodeIdAndStatusOrderByVersionNumberDesc(Long episodeId, String status);
    Optional<AudioVersion> findTopByEpisodeIdOrderByVersionNumberDesc(Long episodeId);
    long countByEpisodeId(Long episodeId);
}
