package com.podcast.collab.dto.stats;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamStatsResponse {

    private long totalPodcasts;
    private long totalEpisodes;
    private long totalMarkers;
    private Double avgResolutionTimeHours;
    private List<Map<String, Object>> markersPerUser;
    private long overdueTasks;
    private List<Map<String, Object>> platformCoverage;
}
