package com.podcast.collab.repo;

import com.podcast.collab.domain.Asset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssetRepository extends JpaRepository<Asset, Long> {
    List<Asset> findByTeamIdOrderByCreatedAtDesc(Long teamId);
    List<Asset> findByTeamIdAndTypeOrderByCreatedAtDesc(Long teamId, com.podcast.collab.domain.Enums.AssetType type);
}
