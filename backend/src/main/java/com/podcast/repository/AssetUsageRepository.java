package com.podcast.repository;

import com.podcast.domain.AssetUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AssetUsageRepository extends JpaRepository<AssetUsage, Long> {
    List<AssetUsage> findByAssetIdOrderByCreatedAtAsc(Long assetId);
    List<AssetUsage> findByEpisodeId(Long episodeId);
    long countByAssetId(Long assetId);
}
