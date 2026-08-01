package com.podcast.collab.dto;

import com.podcast.collab.domain.Enums;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record AssetDtos() {

    public record AssetResponse(Long id, String name, Enums.AssetType type, String fileUrl,
                                String content, Integer usageCount, Instant createdAt) {}

    public record CreateAssetRequest(
            @NotBlank String name,
            @NotNull Enums.AssetType type,
            String fileUrl,
            String content
    ) {}

    public record UpdateAssetRequest(
            String name,
            String fileUrl,
            String content
    ) {}

    public record AssetUsageResponse(Long id, Long assetId, Long episodeId, Long usedAtMs, Instant usedAt) {}

    public record CreateUsageRequest(@NotNull Long episodeId, Long usedAtMs) {}
}
