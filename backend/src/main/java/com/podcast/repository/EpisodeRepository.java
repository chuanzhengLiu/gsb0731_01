package com.podcast.repository;

import com.podcast.domain.Episode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EpisodeRepository extends JpaRepository<Episode, Long> {
    List<Episode> findByPodcastId(Long podcastId);
    List<Episode> findByPodcastIdIn(List<Long> podcastIds);
    Optional<Episode> findByPodcastIdAndNumber(Long podcastId, Integer number);
    boolean existsByPodcastIdAndNumber(Long podcastId, Integer number);
}
