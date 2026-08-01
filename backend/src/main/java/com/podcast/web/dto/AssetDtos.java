package com.podcast.web.dto;

import com.podcast.domain.Asset;
import com.podcast.domain.AssetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class AssetDtos {

    /** Create/update a text asset (audio assets are created via multipart upload). */
    public record TextAssetRequest(
            @NotBlank String name,
            String category,
            @NotBlank String textContent
    ) {}

    public record UpdateAssetRequest(
            String name,
            String category,
            String textContent
    ) {}

    /** Record a use of an asset in an episode (README §4.5). */
    public record RecordUsageRequest(
            @NotNull Long episodeId,
            Long positionMs,
            String note
    ) {}

    public record AssetResponse(
            Long id, Long teamId, String name, String type, String category,
            Long sizeBytes, Long durationMs, String contentType,
            String textContent, Integer usageCount, String previewUrl, String createdAt
    ) {
        public static AssetResponse from(Asset a, String previewUrl) {
            return new AssetResponse(
                    a.getId(), a.getTeamId(), a.getName(), a.getType().name(), a.getCategory(),
                    a.getSizeBytes(), a.getDurationMs(), a.getContentType(),
                    a.getTextContent(), a.getUsageCount(), previewUrl, a.getCreatedAt().toString());
        }
    }

    public record UsageResponse(
            Long id, Long assetId, Long episodeId, Integer episodeNumber,
            String episodeTitle, Long positionMs, String note, String createdAt
    ) {}

    public static AssetType parseType(String type) {
        try {
            return AssetType.valueOf(type.toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }
}
