package com.podcast.collab.repository;

import com.podcast.collab.entity.AssetUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AssetUsageRepository extends JpaRepository<AssetUsage, Long> {
    List<AssetUsage> findByAssetId(Long assetId);
    List<AssetUsage> findByEpisodeId(Long episodeId);
}
