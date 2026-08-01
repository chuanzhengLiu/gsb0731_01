package com.podcast.collab.dto.share;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateShareLinkRequest {

    @NotNull(message = "Episode ID is required")
    private Long episodeId;
}
