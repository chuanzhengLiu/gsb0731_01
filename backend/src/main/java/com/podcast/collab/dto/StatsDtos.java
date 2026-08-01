package com.podcast.collab.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record StatsDtos() {

    public record EpisodeStats(Long episodeId, String title, long durationMs, long totalMarkers,
                               long resolvedMarkers, long pendingMarkers, long versionCount,
                               long productionDays, Map<String, Long> markersByType) {}

    public record TeamStats(long episodeCount, long totalMarkers, long resolvedMarkers,
                            long overdueTasks, double averageMarkersPerEpisode,
                            List<MemberStat> perMember) {}

    public record MemberStat(Long userId, String name, long markersCreated, long tasksAssigned, long tasksDone) {}

    public record DistributionCoverage(String platformName, long total, long published, double coveragePercent,
                                       Double averageReviewHours) {}

    public record StructureComparison(String segmentName, Integer targetSeconds, Long actualSeconds,
                                      Long deltaSeconds) {}

    public record ShareLinkResponse(Long id, Long episodeId, String token, Instant expiresAt,
                                    String shareUrl, Instant createdAt) {}
}
