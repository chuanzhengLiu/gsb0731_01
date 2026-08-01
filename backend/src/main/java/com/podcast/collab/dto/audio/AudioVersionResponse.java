package com.podcast.collab.dto.audio;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AudioVersionResponse {

    private Long id;
    private Long episodeId;
    private Integer versionNumber;
    private String fileUrl;
    private String fileName;
    private Long fileSize;
    private Long durationMs;
    private String waveformUrl;
    private Long uploadedBy;
    private String uploadedByName;
    private Boolean isArchived;
    private LocalDateTime createdAt;
}
