package com.podcast.collab.dto.marker;

import com.podcast.collab.entity.enums.MarkerStatus;
import com.podcast.collab.entity.enums.MarkerType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarkerResponse {

    private Long id;
    private Long audioVersionId;
    private Long startTimeMs;
    private Long endTimeMs;
    private MarkerType type;
    private String description;
    private String screenshotUrl;
    private MarkerStatus status;
    private Long assigneeId;
    private String assigneeName;
    private Long createdBy;
    private String createdByName;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
