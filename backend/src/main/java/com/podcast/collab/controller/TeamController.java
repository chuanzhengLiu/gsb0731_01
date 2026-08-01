package com.podcast.collab.controller;

import com.podcast.collab.dto.common.ApiResponse;
import com.podcast.collab.dto.team.*;
import com.podcast.collab.security.CustomUserDetails;
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

    @PostMapping
    public ApiResponse<TeamResponse> createTeam(@Valid @RequestBody CreateTeamRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        TeamResponse response = teamService.createTeam(request, userId);
        return ApiResponse.ok(response);
    }

    @GetMapping
    public ApiResponse<List<TeamResponse>> getUserTeams() {
        Long userId = SecurityUtils.getCurrentUserId();
        List<TeamResponse> teams = teamService.getUserTeams(userId);
        return ApiResponse.ok(teams);
    }

    @GetMapping("/invitations")
    public ApiResponse<List<InvitationResponse>> getPendingInvitations() {
        CustomUserDetails currentUser = SecurityUtils.getCurrentUser();
        List<InvitationResponse> invitations = teamService.getPendingInvitations(currentUser.getEmail());
        return ApiResponse.ok(invitations);
    }

    @PostMapping("/invitations/accept")
    public ApiResponse<TeamMemberResponse> acceptInvitation(@Valid @RequestBody AcceptInviteRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        TeamMemberResponse response = teamService.acceptInvitation(request, userId);
        return ApiResponse.ok(response);
    }

    @GetMapping("/{id}")
    public ApiResponse<TeamResponse> getTeamById(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        TeamResponse response = teamService.getTeamById(id, userId);
        return ApiResponse.ok(response);
    }

    @GetMapping("/{id}/members")
    public ApiResponse<List<TeamMemberResponse>> getTeamMembers(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        List<TeamMemberResponse> members = teamService.getTeamMembers(id, userId);
        return ApiResponse.ok(members);
    }

    @PostMapping("/{id}/invite")
    public ApiResponse<InvitationResponse> inviteMember(@PathVariable Long id,
                                                         @Valid @RequestBody InviteMemberRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        InvitationResponse response = teamService.inviteMember(id, request, userId);
        return ApiResponse.ok(response);
    }

    @DeleteMapping("/{id}/members/{memberId}")
    public ApiResponse<Void> removeMember(@PathVariable Long id, @PathVariable Long memberId) {
        Long userId = SecurityUtils.getCurrentUserId();
        teamService.removeMember(id, memberId, userId);
        return ApiResponse.ok("Member removed successfully", null);
    }

    @PatchMapping("/{id}/members/{memberId}")
    public ApiResponse<TeamMemberResponse> updateMemberRole(@PathVariable Long id,
                                                             @PathVariable Long memberId,
                                                             @Valid @RequestBody UpdateMemberRoleRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        TeamMemberResponse response = teamService.updateMemberRole(id, memberId, request, userId);
        return ApiResponse.ok(response);
    }

    @GetMapping("/{id}/invitations")
    public ApiResponse<List<InvitationResponse>> getTeamInvitations(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        List<InvitationResponse> invitations = teamService.getTeamInvitations(id, userId);
        return ApiResponse.ok(invitations);
    }
}
