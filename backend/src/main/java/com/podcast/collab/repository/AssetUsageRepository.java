package com.podcast.collab.repository;

import com.podcast.collab.entity.AssetUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AssetUsageRepository extends JpaRepository<AssetUsage, Long> {
    List<AssetUsage> findByAssetId(Long assetId);
    List<AssetUsage> findByEpisodeId(Long episodeId);
}
