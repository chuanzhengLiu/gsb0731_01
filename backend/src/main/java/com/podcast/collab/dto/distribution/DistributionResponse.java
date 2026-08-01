package com.podcast.collab.dto.distribution;

import com.podcast.collab.entity.enums.DistributionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DistributionResponse {

    private Long id;
    private Long episodeId;
    private Long platformId;
    private DistributionStatus status;
    private String platformDataJson;
    private LocalDateTime submittedAt;
    private LocalDateTime publishedAt;
    private LocalDateTime reviewedAt;
    private String rejectionReason;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String platformName;
    private String platformDisplayName;
}
