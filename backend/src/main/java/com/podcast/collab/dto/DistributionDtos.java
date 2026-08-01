package com.podcast.collab.dto;

import com.podcast.collab.domain.Enums;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record DistributionDtos() {

    public record PlatformResponse(Long id, String name, String code,
                                   String rssRequiredFieldsJson, String categoryOptionsJson) {}

    public record PlatformAccountResponse(Long id, Long platformId, String platformName,
                                          String displayName, Instant createdAt) {}

    public record CreateAccountRequest(@NotNull Long platformId, @NotNull String displayName,
                                       String credentialsJson) {}

    public record DistributionResponse(Long id, Long episodeId, Long platformAccountId,
                                       String platformName, String accountDisplayName,
                                       Enums.DistributionStatus status, Instant submittedAt,
                                       Instant publishedAt, String platformDataJson,
                                       String rejectionReason, Instant updatedAt) {}

    public record CreateDistributionRequest(@NotNull Long platformAccountId, String platformDataJson) {}

    public record UpdateDistributionRequest(Enums.DistributionStatus status, String platformDataJson,
                                            String rejectionReason) {}
}
