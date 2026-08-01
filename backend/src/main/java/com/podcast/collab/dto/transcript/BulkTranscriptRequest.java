package com.podcast.collab.dto.transcript;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkTranscriptRequest {

    @NotEmpty(message = "segments must not be empty")
    @Valid
    private List<Segment> segments;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Segment {

        private Long startTimeMs;

        private Long endTimeMs;

        private String text;

        private String speaker;
    }
}
