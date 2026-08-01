package com.podcast.collab.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "audio_versions")
@Getter
@Setter
public class AudioVersion extends BaseEntity {
    @Column(name = "episode_id", nullable = false)
    private Long episodeId;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "file_url", nullable = false)
    private String fileUrl;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "duration_ms", nullable = false)
    private Long durationMs;

    @Column(name = "peaks_url")
    private String peaksUrl;

    @Column(nullable = false)
    private boolean archived = false;

    @Column(name = "uploaded_by", nullable = false)
    private Long uploadedBy;
}
