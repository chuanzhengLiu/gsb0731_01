package com.podcast.collab.repo;

import com.podcast.collab.domain.Podcast;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PodcastRepository extends JpaRepository<Podcast, Long> {
    List<Podcast> findByTeamIdOrderByCreatedAtDesc(Long teamId);
}
