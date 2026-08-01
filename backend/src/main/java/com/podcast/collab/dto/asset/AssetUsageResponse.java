package com.podcast.collab.dto.asset;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetUsageResponse {

    private Long id;
    private Long assetId;
    private Long episodeId;
    private Long timeMs;
    private LocalDateTime usedAt;
    private String assetName;
    private String episodeTitle;
}
