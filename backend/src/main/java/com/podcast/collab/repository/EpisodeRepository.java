package com.podcast.collab.repository;

import com.podcast.collab.entity.Episode;
import com.podcast.collab.entity.enums.EpisodeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface EpisodeRepository extends JpaRepository<Episode, Long> {
    List<Episode> findByPodcastIdOrderByNumberDesc(Long podcastId);
    List<Episode> findByPodcastIdAndStatusOrderByNumberDesc(Long podcastId, EpisodeStatus status);
    long countByPodcastIdAndStatus(Long podcastId, EpisodeStatus status);
}
