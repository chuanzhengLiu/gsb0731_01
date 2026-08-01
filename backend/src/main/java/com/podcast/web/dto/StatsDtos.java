package com.podcast.web.dto;

import java.util.List;

public class StatsDtos {

    /** Per-episode metrics (README §4.6 单集数据). */
    public record EpisodeStat(
            Long episodeId,
            Integer episodeNumber,
            String episodeTitle,
            String podcastName,
            Long durationMs,
            long markerCount,        // 反映修改轮次
            long versionCount,
            Integer cycleDays        // 录制到发布的周期天数（null 未发布/无录制日期）
    ) {}

    /** Team efficiency metrics (README §4.6 团队效率). */
    public record MemberEfficiency(
            Long userId,
            String name,
            String role,
            long markersRaised,      // 人均处理标记数（其提出的标记）
            long openTasks,
            long overdueTasks        // 逾期任务数
    ) {}

    /** Distribution coverage per platform (README §4.6 分发覆盖). */
    public record PlatformCoverage(
            Long platformId,
            String platformName,
            long total,
            long published,
            double publishRate,           // 上架率
            Double avgReviewHours         // 平均审核时长（提交→上线）
    ) {}

    public record TeamStats(
            List<EpisodeStat> episodes,
            List<MemberEfficiency> members,
            List<PlatformCoverage> platforms,
            double avgMarkersPerEpisode,   // 平均审听轮次的近似（每集标记数）
            long totalOverdueTasks
    ) {}
}
