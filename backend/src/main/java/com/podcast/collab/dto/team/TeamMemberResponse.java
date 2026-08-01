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
public class TeamMemberResponse {

    private Long id;
    private Long userId;
    private String email;
    private String name;
    private TeamRole roleInTeam;
    private LocalDateTime joinedAt;
    private String avatarUrl;
}
