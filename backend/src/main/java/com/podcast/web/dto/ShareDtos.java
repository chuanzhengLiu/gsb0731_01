package com.podcast.web.dto;

import com.podcast.domain.ShareLink;

import java.util.List;

public class ShareDtos {

    /** Admin/producer view of a created share link. */
    public record ShareLinkResponse(
            Long id, Long episodeId, String url,
            String expiresAt, boolean revoked, boolean expired,
            Integer accessCount, String lastAccessedAt, String createdAt
    ) {
        public static ShareLinkResponse from(ShareLink s, String url, boolean expired) {
            return new ShareLinkResponse(
                    s.getId(), s.getEpisodeId(), url,
                    s.getExpiresAt().toString(), s.isRevoked(), expired,
                    s.getAccessCount(),
                    s.getLastAccessedAt() != null ? s.getLastAccessedAt().toString() : null,
                    s.getCreatedAt().toString());
        }
    }

    /** Read-only shared episode view for a guest (README §3.1). */
    public record SharedEpisodeView(
            Long episodeId,
            Integer number,
            String title,
            String theme,
            String status,
            String podcastName,
            AudioView audio,
            List<MarkerView> markers,
            List<SegmentView> transcript
    ) {}

    public record AudioView(
            Long audioVersionId, Integer versionNumber, Long durationMs, String streamUrl
    ) {}

    public record MarkerView(
            Long id, Long startTimeMs, Long endTimeMs, String type, String description, String status
    ) {}

    public record SegmentView(
            Long id, Long startTimeMs, Long endTimeMs, String text, String speaker, String speakerColor
    ) {}
}
