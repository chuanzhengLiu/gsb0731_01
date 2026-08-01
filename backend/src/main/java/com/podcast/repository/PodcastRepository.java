package com.podcast.repository;

import com.podcast.domain.Podcast;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PodcastRepository extends JpaRepository<Podcast, Long> {
    List<Podcast> findByTeamId(Long teamId);
    // Team isolation: always fetch scoped by team_id (README §8).
    Optional<Podcast> findByIdAndTeamId(Long id, Long teamId);
}
