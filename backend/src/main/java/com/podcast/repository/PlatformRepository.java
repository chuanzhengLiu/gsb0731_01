package com.podcast.repository;

import com.podcast.domain.Platform;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PlatformRepository extends JpaRepository<Platform, Long> {
    Optional<Platform> findByName(String name);
    boolean existsByName(String name);
}
