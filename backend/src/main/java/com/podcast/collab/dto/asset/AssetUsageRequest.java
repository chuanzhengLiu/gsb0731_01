package com.podcast.collab.dto.asset;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssetUsageRequest {

    @NotNull(message = "Episode ID is required")
    private Long episodeId;

    private Long timeMs;
}
