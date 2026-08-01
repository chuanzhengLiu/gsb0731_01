package com.podcast.collab.dto.share;

import com.podcast.collab.entity.enums.EpisodeStatus;
import com.podcast.collab.entity.enums.MarkerStatus;
import com.podcast.collab.entity.enums.MarkerType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SharedEpisodeResponse {

    private Long episodeId;
    private Integer number;
    private String title;
    private EpisodeStatus status;
    private String finalAudioUrl;
    private String podcastName;
    private SharedAudioVersion latestAudioVersion;
    private List<SharedMarker> markers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SharedAudioVersion {
        private Long id;
        private Integer versionNumber;
        private String fileUrl;
        private Long durationMs;
        private String waveformUrl;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SharedMarker {
        private Long id;
        private Long startTimeMs;
        private Long endTimeMs;
        private MarkerType type;
        private String description;
        private MarkerStatus status;
    }
}
