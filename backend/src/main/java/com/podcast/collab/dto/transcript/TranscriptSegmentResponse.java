package com.podcast.collab.dto.transcript;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptSegmentResponse {

    private Long id;
    private Long audioVersionId;
    private Long startTimeMs;
    private Long endTimeMs;
    private String text;
    private String speaker;
    private Integer segmentOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
