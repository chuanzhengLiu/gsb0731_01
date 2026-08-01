package com.podcast.collab.dto.transcript;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTranscriptSegmentRequest {

    private String text;

    private String speaker;

    private Long startTimeMs;

    private Long endTimeMs;
}
