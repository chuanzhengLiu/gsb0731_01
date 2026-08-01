package com.podcast.repository;

import com.podcast.domain.Asset;
import com.podcast.domain.AssetType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AssetRepository extends JpaRepository<Asset, Long> {
    List<Asset> findByTeamIdOrderByCreatedAtDesc(Long teamId);
    List<Asset> findByTeamIdAndType(Long teamId, AssetType type);
}
