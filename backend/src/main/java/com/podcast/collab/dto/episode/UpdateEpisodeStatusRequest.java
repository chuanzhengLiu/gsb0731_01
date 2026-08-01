package com.podcast.collab.dto.episode;

import com.podcast.collab.entity.enums.EpisodeStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateEpisodeStatusRequest {

    @NotNull(message = "Episode status is required")
    private EpisodeStatus status;
}
