package com.podcast.collab.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record TranscriptDtos() {

    public record SegmentResponse(Long id, Long audioVersionId, Long startTimeMs, Long endTimeMs,
                                  String text, String speaker, Integer seq) {}

    public record SegmentInput(
            @NotNull Long startTimeMs,
            @NotNull Long endTimeMs,
            @NotBlank String text,
            String speaker
    ) {}

    public record SaveTranscriptRequest(
            @NotNull @Valid List<SegmentInput> segments
    ) {}
}
