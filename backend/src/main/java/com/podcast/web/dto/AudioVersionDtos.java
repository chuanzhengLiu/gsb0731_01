package com.podcast.web.dto;

import com.podcast.domain.AudioVersion;

public class AudioVersionDtos {

    public record AudioVersionResponse(
            Long id,
            Long episodeId,
            Integer versionNumber,
            String originalName,
            String contentType,
            Long sizeBytes,
            Long durationMs,
            boolean archived,
            boolean hasWaveform,
            Long uploadedBy,
            String createdAt,
            String streamUrl
    ) {
        public static AudioVersionResponse from(AudioVersion v, String streamUrl) {
            return new AudioVersionResponse(
                    v.getId(), v.getEpisodeId(), v.getVersionNumber(),
                    v.getOriginalName(), v.getContentType(), v.getSizeBytes(),
                    v.getDurationMs(), v.isArchived(), v.getWaveformJson() != null,
                    v.getUploadedBy(), v.getCreatedAt().toString(), streamUrl);
        }
    }
}
