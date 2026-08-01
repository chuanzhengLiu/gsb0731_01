package com.podcast.collab.web;

import com.podcast.collab.dto.TeamDtos.InvitationResponse;
import com.podcast.collab.service.TeamService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PublicInvitationController {

    private final TeamService teamService;

    public PublicInvitationController(TeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping("/invitations/{token}")
    public InvitationResponse getInvitation(@PathVariable String token) {
        return teamService.getInvitation(token);
    }
}
