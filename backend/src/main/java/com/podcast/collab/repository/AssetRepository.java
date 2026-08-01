package com.podcast.collab.repository;

import com.podcast.collab.entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AssetRepository extends JpaRepository<Asset, Long> {
    List<Asset> findByTeamId(Long teamId);
    List<Asset> findByTeamIdAndType(Long teamId, String type);
}
