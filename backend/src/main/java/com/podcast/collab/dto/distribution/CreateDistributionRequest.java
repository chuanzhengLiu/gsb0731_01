package com.podcast.collab.dto.distribution;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateDistributionRequest {

    @NotNull(message = "Platform ID is required")
    private Long platformId;

    private String platformDataJson;
}
