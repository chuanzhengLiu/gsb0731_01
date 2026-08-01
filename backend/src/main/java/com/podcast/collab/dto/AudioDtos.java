package com.podcast.collab.dto;

import com.podcast.collab.domain.Enums;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record AudioDtos() {

    public record AudioVersionResponse(Long id, Long episodeId, Integer versionNumber, String fileUrl,
                                       String fileName, String mimeType, Long fileSize, Long durationMs,
                                       String peaksUrl, boolean archived, Long uploadedBy,
                                       String uploaderName, String downloadUrl, Instant createdAt) {}

    public record MarkerResponse(Long id, Long audioVersionId, Long startTimeMs, Long endTimeMs,
                                 Enums.MarkerType type, String description, Enums.MarkerStatus status,
                                 String screenshotUrl, Long createdBy, String creatorName,
                                 Long resolvedBy, Instant resolvedAt, Instant createdAt) {}

    public record CreateMarkerRequest(
            @NotNull Long startTimeMs,
            Long endTimeMs,
            @NotNull Enums.MarkerType type,
            @NotNull String description,
            String screenshotUrl
    ) {}

    public record UpdateMarkerRequest(
            Long startTimeMs,
            Long endTimeMs,
            Enums.MarkerType type,
            String description,
            Enums.MarkerStatus status,
            String screenshotUrl
    ) {}
}
