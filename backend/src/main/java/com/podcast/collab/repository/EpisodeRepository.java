package com.podcast.collab.repository;

import com.podcast.collab.entity.Episode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EpisodeRepository extends JpaRepository<Episode, Long> {
    List<Episode> findByPodcastIdOrderByNumberDesc(Long podcastId);
}
