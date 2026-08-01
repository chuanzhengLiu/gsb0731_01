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
public class InvitationResponse {

    private Long id;
    private Long teamId;
    private String email;
    private TeamRole roleInTeam;
    private String token;
    private LocalDateTime expiresAt;
    private LocalDateTime acceptedAt;
}
