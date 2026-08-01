package com.podcast.collab.dto.audio;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadAudioResponse {

    private Long versionId;
    private Integer versionNumber;
    private Long durationMs;
    private String waveformUrl;
}
