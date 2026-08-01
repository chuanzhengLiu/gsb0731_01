package com.podcast.web.dto;

import com.podcast.domain.MarkerStatus;
import com.podcast.domain.MarkerType;
import com.podcast.domain.TimelineMarker;
import jakarta.validation.constraints.NotNull;

public class MarkerDtos {

    public record CreateMarkerRequest(
            @NotNull Long startTimeMs,
            Long endTimeMs,            // null = point marker (P0)
            @NotNull MarkerType type,
            String description,
            String screenshotUrl
    ) {}

    public record UpdateMarkerRequest(
            Long startTimeMs,
            Long endTimeMs,
            MarkerType type,
            String description,
            String screenshotUrl,
            MarkerStatus status
    ) {}

    /** Dedicated status-flow request (README §4.2 标记状态流转). */
    public record ChangeStatusRequest(
            @NotNull MarkerStatus status
    ) {}

    public record MarkerResponse(
            Long id,
            Long audioVersionId,
            Long startTimeMs,
            Long endTimeMs,
            String type,
            String description,
            String screenshotUrl,
            String status,
            Long createdBy,
            String createdAt
    ) {
        public static MarkerResponse from(TimelineMarker m) {
            return new MarkerResponse(
                    m.getId(), m.getAudioVersionId(), m.getStartTimeMs(), m.getEndTimeMs(),
                    m.getType().name(), m.getDescription(), m.getScreenshotUrl(),
                    m.getStatus().name(), m.getCreatedBy(), m.getCreatedAt().toString());
        }
    }
}
