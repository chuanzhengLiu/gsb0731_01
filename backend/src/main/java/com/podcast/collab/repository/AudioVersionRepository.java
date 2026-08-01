package com.podcast.collab.repository;

import com.podcast.collab.entity.AudioVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface AudioVersionRepository extends JpaRepository<AudioVersion, Long> {
    List<AudioVersion> findByEpisodeIdOrderByVersionNumberDesc(Long episodeId);
    Optional<AudioVersion> findTopByEpisodeIdOrderByVersionNumberDesc(Long episodeId);
    Optional<AudioVersion> findByEpisodeIdAndVersionNumber(Long episodeId, Integer versionNumber);
    long countByEpisodeId(Long episodeId);
}
