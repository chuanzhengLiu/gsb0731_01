package com.podcast.web;

import com.podcast.service.InvitationService;
import com.podcast.web.dto.InvitationDtos.AcceptInvitationRequest;
import com.podcast.web.dto.InvitationDtos.CreateInvitationRequest;
import com.podcast.web.dto.InvitationDtos.InvitationResponse;
import com.podcast.web.dto.TokenResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class InvitationController {

    private final InvitationService service;

    public InvitationController(InvitationService service) {
        this.service = service;
    }

    /** Admin invites a member by email (README §3.2). */
    @PostMapping("/invitations")
    @PreAuthorize("hasRole('ADMIN')")
    public InvitationResponse invite(@Valid @RequestBody CreateInvitationRequest req) {
        var inv = service.invite(req);
        return InvitationResponse.from(inv, service.isExpired(inv));
    }

    /** List invitations of the current team (admin). */
    @GetMapping("/invitations")
    @PreAuthorize("hasRole('ADMIN')")
    public List<InvitationResponse> list() {
        return service.listForCurrentTeam().stream()
                .map(inv -> InvitationResponse.from(inv, service.isExpired(inv)))
                .toList();
    }

    @DeleteMapping("/invitations/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> revoke(@PathVariable Long id) {
        service.revoke(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Public endpoint: an invitee accepts the link and their account is
     * provisioned. Returns tokens so they are logged in immediately.
     */
    @PostMapping("/invitations/accept")
    public TokenResponse accept(@Valid @RequestBody AcceptInvitationRequest req, HttpServletRequest http) {
        return service.accept(req, http);
    }
}
