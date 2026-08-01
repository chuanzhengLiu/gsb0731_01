package com.podcast.domain;

import jakarta.persistence.*;

/**
 * A time-aligned transcript segment (README §4.3 转写文本对齐). Segments are
 * ordered by {@code segmentIndex} and each carries a start/end timestamp so the
 * player can seek to a sentence. {@code speaker}/{@code speakerColor} support
 * speaker separation display; {@code edited} flags human-corrected text.
 */
@Entity
@Table(name = "transcript_segment")
public class TranscriptSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "audio_version_id", nullable = false)
    private Long audioVersionId;

    @Column(name = "segment_index", nullable = false)
    private Integer segmentIndex = 0;

    @Column(name = "start_time_ms", nullable = false)
    private Long startTimeMs;

    @Column(name = "end_time_ms", nullable = false)
    private Long endTimeMs;

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    @Column(length = 64)
    private String speaker;

    @Column(name = "speaker_color", length = 16)
    private String speakerColor;

    @Column(nullable = false)
    private boolean edited = false;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAudioVersionId() { return audioVersionId; }
    public void setAudioVersionId(Long audioVersionId) { this.audioVersionId = audioVersionId; }

    public Integer getSegmentIndex() { return segmentIndex; }
    public void setSegmentIndex(Integer segmentIndex) { this.segmentIndex = segmentIndex; }

    public Long getStartTimeMs() { return startTimeMs; }
    public void setStartTimeMs(Long startTimeMs) { this.startTimeMs = startTimeMs; }

    public Long getEndTimeMs() { return endTimeMs; }
    public void setEndTimeMs(Long endTimeMs) { this.endTimeMs = endTimeMs; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getSpeaker() { return speaker; }
    public void setSpeaker(String speaker) { this.speaker = speaker; }

    public String getSpeakerColor() { return speakerColor; }
    public void setSpeakerColor(String speakerColor) { this.speakerColor = speakerColor; }

    public boolean isEdited() { return edited; }
    public void setEdited(boolean edited) { this.edited = edited; }
}
