package com.podcast.collab.dto.podcast;

import com.podcast.collab.entity.enums.PodcastType;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class UpdatePodcastRequest {

    private String name;

    private PodcastType type;

    private String updateFrequency;

    @Positive(message = "Target duration must be positive")
    private Integer targetDuration;

    private String structureTemplateJson;

    private String description;
}
