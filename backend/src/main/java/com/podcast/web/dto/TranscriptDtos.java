package com.podcast.web.dto;

import com.podcast.domain.MarkerType;
import com.podcast.domain.TranscriptSegment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class TranscriptDtos {

    /** Human correction of a segment's text (README §4.3 转写编辑). */
    public record UpdateSegmentRequest(
            @NotBlank String text,
            String speaker
    ) {}

    /**
     * Create a marker anchored to a transcript segment (README §4.3 在转写文本上
     * 直接添加标记). The segment's time range seeds the marker's start/end.
     */
    public record SegmentMarkerRequest(
            @NotNull MarkerType type,
            String description,
            boolean asRange           // true => range marker spanning the segment
    ) {}

    public record SegmentResponse(
            Long id,
            Long audioVersionId,
            Integer segmentIndex,
            Long startTimeMs,
            Long endTimeMs,
            String text,
            String speaker,
            String speakerColor,
            boolean edited
    ) {
        public static SegmentResponse from(TranscriptSegment s) {
            return new SegmentResponse(
                    s.getId(), s.getAudioVersionId(), s.getSegmentIndex(),
                    s.getStartTimeMs(), s.getEndTimeMs(), s.getText(),
                    s.getSpeaker(), s.getSpeakerColor(), s.isEdited());
        }
    }
}
