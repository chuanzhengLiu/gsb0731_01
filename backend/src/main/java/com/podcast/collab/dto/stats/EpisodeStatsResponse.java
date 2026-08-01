package com.podcast.collab.dto.stats;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EpisodeStatsResponse {

    private Long episodeId;
    private String title;
    private Long durationMs;
    private long totalMarkers;
    private long pendingMarkers;
    private long resolvedMarkers;
    private long taskCount;
    private Long daysFromRecordToPublish;
    private long distributionCount;
    private long publishedPlatformCount;
}
