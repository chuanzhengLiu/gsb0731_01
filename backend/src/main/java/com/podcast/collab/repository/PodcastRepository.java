package com.podcast.collab.repository;

import com.podcast.collab.entity.Podcast;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PodcastRepository extends JpaRepository<Podcast, Long> {
    List<Podcast> findByTeamId(Long teamId);
}
