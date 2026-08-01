package com.podcast.collab.repository;

import com.podcast.collab.entity.Asset;
import com.podcast.collab.entity.enums.AssetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AssetRepository extends JpaRepository<Asset, Long> {
    List<Asset> findByTeamId(Long teamId);
    List<Asset> findByTeamIdAndType(Long teamId, AssetType type);
    List<Asset> findByTeamIdAndCategory(Long teamId, String category);
}
