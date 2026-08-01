package com.podcast.web.dto;

import com.podcast.domain.Distribution;
import com.podcast.domain.DistributionStatus;
import com.podcast.domain.Platform;
import com.podcast.domain.PlatformAccount;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class DistributionDtos {

    public record PlatformResponse(
            Long id, String name, String rssRequiredFieldsJson, String categoryOptionsJson
    ) {
        public static PlatformResponse from(Platform p) {
            return new PlatformResponse(p.getId(), p.getName(),
                    p.getRssRequiredFieldsJson(), p.getCategoryOptionsJson());
        }
    }

    public record UpsertAccountRequest(
            @NotNull Long platformId,
            @NotBlank String accountName,
            String accountUrl,
            String notes
    ) {}

    public record AccountResponse(
            Long id, Long platformId, String platformName,
            String accountName, String accountUrl, String notes
    ) {
        public static AccountResponse from(PlatformAccount a, String platformName) {
            return new AccountResponse(a.getId(), a.getPlatformId(), platformName,
                    a.getAccountName(), a.getAccountUrl(), a.getNotes());
        }
    }

    /** Create/select a platform for an episode's distribution + platform info. */
    public record UpsertDistributionRequest(
            @NotNull Long platformId,
            String platformDataJson
    ) {}

    public record ChangeDistributionStatusRequest(
            @NotNull DistributionStatus status
    ) {}

    public record DistributionResponse(
            Long id, Long episodeId, Long platformId, String platformName,
            String status, String submittedAt, String publishedAt, String platformDataJson
    ) {
        public static DistributionResponse from(Distribution d, String platformName) {
            return new DistributionResponse(
                    d.getId(), d.getEpisodeId(), d.getPlatformId(), platformName,
                    d.getStatus().name(),
                    d.getSubmittedAt() != null ? d.getSubmittedAt().toString() : null,
                    d.getPublishedAt() != null ? d.getPublishedAt().toString() : null,
                    d.getPlatformDataJson());
        }
    }

    /** A publish-calendar entry (README §4.4 发布日历). */
    public record CalendarEntry(
            Long episodeId, Integer episodeNumber, String episodeTitle,
            Long podcastId, String podcastName,
            String date, String status
    ) {}
}
