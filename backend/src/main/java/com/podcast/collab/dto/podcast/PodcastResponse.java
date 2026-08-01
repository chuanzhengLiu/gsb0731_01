package com.podcast.collab.dto.podcast;

import com.podcast.collab.entity.enums.PodcastType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PodcastResponse {

    private Long id;
    private Long teamId;
    private String name;
    private PodcastType type;
    private String updateFrequency;
    private Integer targetDuration;
    private String structureTemplateJson;
    private String description;
    private String coverImageUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
