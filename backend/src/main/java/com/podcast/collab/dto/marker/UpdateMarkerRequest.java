package com.podcast.collab.dto.marker;

import com.podcast.collab.entity.enums.MarkerStatus;
import com.podcast.collab.entity.enums.MarkerType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMarkerRequest {

    private Long startTimeMs;

    private Long endTimeMs;

    private MarkerType type;

    private String description;

    private MarkerStatus status;

    private Long assigneeId;
}
