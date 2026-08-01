package com.podcast.collab.dto.team;

import com.podcast.collab.entity.enums.TeamRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateMemberRoleRequest {

    @NotNull(message = "Role in team is required")
    private TeamRole roleInTeam;
}
