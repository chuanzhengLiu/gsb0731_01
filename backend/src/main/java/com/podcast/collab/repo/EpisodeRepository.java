package com.podcast.collab.repo;

import com.podcast.collab.domain.Episode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EpisodeRepository extends JpaRepository<Episode, Long> {
    List<Episode> findByPodcastIdOrderByNumberDesc(Long podcastId);
    long countByPodcastId(Long podcastId);
}
