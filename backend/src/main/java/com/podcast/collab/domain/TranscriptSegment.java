package com.podcast.collab.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "transcript_segments")
@Getter
@Setter
public class TranscriptSegment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "audio_version_id", nullable = false)
    private Long audioVersionId;

    @Column(name = "start_time_ms", nullable = false)
    private Long startTimeMs;

    @Column(name = "end_time_ms", nullable = false)
    private Long endTimeMs;

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    private String speaker;

    @Column(nullable = false)
    private Integer seq;
}
