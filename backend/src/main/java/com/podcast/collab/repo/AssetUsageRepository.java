package com.podcast.collab.repo;

import com.podcast.collab.domain.AssetUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssetUsageRepository extends JpaRepository<AssetUsage, Long> {
    List<AssetUsage> findByAssetIdOrderByUsedAtDesc(Long assetId);
    List<AssetUsage> findByEpisodeIdOrderByUsedAtDesc(Long episodeId);
}
