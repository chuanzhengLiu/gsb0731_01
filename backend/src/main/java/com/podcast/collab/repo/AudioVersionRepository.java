package com.podcast.collab.repo;

import com.podcast.collab.domain.AudioVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AudioVersionRepository extends JpaRepository<AudioVersion, Long> {
    List<AudioVersion> findByEpisodeIdOrderByVersionNumberDesc(Long episodeId);
    Optional<AudioVersion> findFirstByEpisodeIdOrderByVersionNumberDesc(Long episodeId);
    Optional<AudioVersion> findByEpisodeIdAndVersionNumber(Long episodeId, Integer versionNumber);
    Optional<AudioVersion> findFirstByFileUrl(String fileUrl);
    long countByEpisodeId(Long episodeId);
}
