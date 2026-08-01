package com.podcast.collab.dto.distribution;

import com.podcast.collab.entity.enums.DistributionStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateDistributionStatusRequest {

    @NotNull(message = "Status is required")
    private DistributionStatus status;

    private String rejectionReason;
}
