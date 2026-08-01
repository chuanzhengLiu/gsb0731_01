package com.podcast.collab.dto.team;

import com.podcast.collab.entity.enums.TeamRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamResponse {

    private Long id;
    private String name;
    private Long createdBy;
    private LocalDateTime createdAt;
    private TeamRole currentUserRole;
}
