package com.podcast.collab.dto.marker;

import com.podcast.collab.entity.enums.MarkerType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMarkerRequest {

    @NotNull(message = "startTimeMs is required")
    private Long startTimeMs;

    private Long endTimeMs;

    @NotNull(message = "type is required")
    private MarkerType type;

    private String description;

    private Long assigneeId;

    private String screenshotUrl;
}
