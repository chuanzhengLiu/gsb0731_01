package com.podcast.collab.web;

import com.podcast.collab.dto.TeamDtos.*;
import com.podcast.collab.security.CurrentUser;
import com.podcast.collab.security.SecurityUtils;
import com.podcast.collab.service.TeamService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/teams")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping
    public List<TeamResponse> myTeams() {
        return teamService.myTeams(SecurityUtils.requireUser());
    }

    @PatchMapping("/{teamId}")
    public TeamResponse update(@PathVariable Long teamId, @Valid @RequestBody UpdateTeamRequest req) {
        return teamService.updateTeam(teamId, req, SecurityUtils.requireUser());
    }

    @GetMapping("/{teamId}/members")
    public List<MemberResponse> members(@PathVariable Long teamId) {
        return teamService.listMembers(teamId, SecurityUtils.requireUser());
    }

    @PostMapping("/{teamId}/invitations")
    public InvitationResponse invite(@PathVariable Long teamId, @Valid @RequestBody InviteRequest req) {
        return teamService.invite(teamId, req, SecurityUtils.requireUser());
    }

    @GetMapping("/{teamId}/invitations")
    public List<InvitationResponse> invitations(@PathVariable Long teamId) {
        return teamService.listInvitations(teamId, SecurityUtils.requireUser());
    }

    @PatchMapping("/{teamId}/members/{userId}")
    public MemberResponse updateRole(@PathVariable Long teamId, @PathVariable Long userId,
                                     @Valid @RequestBody UpdateRoleRequest req) {
        return teamService.updateRole(teamId, userId, req, SecurityUtils.requireUser());
    }

    @DeleteMapping("/{teamId}/members/{userId}")
    public void removeMember(@PathVariable Long teamId, @PathVariable Long userId) {
        teamService.removeMember(teamId, userId, SecurityUtils.requireUser());
    }
}
