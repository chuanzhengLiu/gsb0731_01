package com.podcast.collab.repository;

import com.podcast.collab.entity.Platform;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PlatformRepository extends JpaRepository<Platform, Long> {
    List<Platform> findByTeamId(Long teamId);
}
