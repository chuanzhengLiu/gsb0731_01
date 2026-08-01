package com.podcast.collab.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "transcript_segments")
public class TranscriptSegment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long audioVersionId;

    @Column(nullable = false)
    private Long startTimeMs;

    @Column(nullable = false)
    private Long endTimeMs;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    private String speaker;

    @Column(nullable = false)
    private boolean edited = false;
}
