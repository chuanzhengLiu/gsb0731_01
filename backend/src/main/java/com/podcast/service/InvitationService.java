package com.podcast.service;

import com.podcast.config.AppProperties;
import com.podcast.domain.*;
import com.podcast.repository.*;
import com.podcast.security.SecurityUtils;
import com.podcast.security.TokenUtil;
import com.podcast.web.ApiException;
import com.podcast.web.dto.InvitationDtos.AcceptInvitationRequest;
import com.podcast.web.dto.InvitationDtos.CreateInvitationRequest;
import com.podcast.web.dto.TokenResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Team member invitations (README §3.2 邀请机制): an ADMIN invites by email,
 * the link is valid 24h, and accepting it provisions the member's account
 * with the granted role. Tokens are stored hashed, mirroring refresh tokens.
 */
@Service
public class InvitationService {

    private final InvitationRepository invitationRepo;
    private final UserRepository userRepo;
    private final TeamRepository teamRepo;
    private final TeamMemberRepository teamMemberRepo;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties props;
    private final EmailService emailService;
    private final AuditService auditService;
    private final AuthService authService;

    public InvitationService(InvitationRepository invitationRepo, UserRepository userRepo,
                             TeamRepository teamRepo, TeamMemberRepository teamMemberRepo,
                             PasswordEncoder passwordEncoder, AppProperties props,
                             EmailService emailService, AuditService auditService,
                             AuthService authService) {
        this.invitationRepo = invitationRepo;
        this.userRepo = userRepo;
        this.teamRepo = teamRepo;
        this.teamMemberRepo = teamMemberRepo;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
        this.emailService = emailService;
        this.auditService = auditService;
        this.authService = authService;
    }

    /** Creates an invitation for the current admin's team and emails the 24h link. */
    @Transactional
    public Invitation invite(CreateInvitationRequest req) {
        Long teamId = SecurityUtils.currentTeamId();
        if (teamId == null) {
            throw ApiException.forbidden("当前用户未加入任何团队");
        }
        if (req.roleInTeam() == Role.GUEST) {
            // Guests use share links (README §3.1), not member invitations.
            throw ApiException.badRequest("访客不通过邀请加入，请使用分享链接");
        }
        if (userRepo.findByEmail(req.email()).filter(u -> teamId.equals(u.getTeamId())).isPresent()) {
            throw ApiException.conflict("该邮箱已是团队成员");
        }

        String rawToken = TokenUtil.generateRawToken();
        Invitation inv = new Invitation();
        inv.setTeamId(teamId);
        inv.setEmail(req.email());
        inv.setRoleInTeam(req.roleInTeam());
        inv.setTokenHash(TokenUtil.sha256(rawToken));
        inv.setInvitedBy(SecurityUtils.currentUserId());
        inv.setExpiresAt(Instant.now().plusSeconds(props.getInvitation().getTtlSeconds()));
        inv = invitationRepo.save(inv);

        Team team = teamRepo.findById(teamId).orElse(null);
        emailService.sendInvitation(req.email(), team != null ? team.getName() : "团队", rawToken);
        auditService.log("INVITATION_CREATE", "Invitation", inv.getId(),
                "email=" + req.email() + " role=" + req.roleInTeam());
        return inv;
    }

    @Transactional(readOnly = true)
    public List<Invitation> listForCurrentTeam() {
        Long teamId = SecurityUtils.currentTeamId();
        if (teamId == null) {
            throw ApiException.forbidden("当前用户未加入任何团队");
        }
        return invitationRepo.findByTeamIdOrderByCreatedAtDesc(teamId);
    }

    /** Revokes a pending invitation (admin, same team only). */
    @Transactional
    public void revoke(Long invitationId) {
        Long teamId = SecurityUtils.currentTeamId();
        Invitation inv = invitationRepo.findById(invitationId)
                .orElseThrow(() -> ApiException.notFound("邀请不存在"));
        if (!inv.getTeamId().equals(teamId)) {
            throw ApiException.notFound("邀请不存在"); // don't leak cross-team
        }
        inv.setRevoked(true);
        invitationRepo.save(inv);
        auditService.log("INVITATION_REVOKE", "Invitation", inv.getId(), null);
    }

    /**
     * Accepts an invitation: validates the token/expiry and provisions the
     * member's user account + team membership, then logs them in.
     */
    @Transactional
    public TokenResponse accept(AcceptInvitationRequest req, HttpServletRequest http) {
        Invitation inv = invitationRepo.findByTokenHash(TokenUtil.sha256(req.token()))
                .orElseThrow(() -> ApiException.badRequest("邀请链接无效"));
        if (inv.isRevoked()) {
            throw ApiException.badRequest("邀请已被撤销");
        }
        if (inv.isAccepted()) {
            throw ApiException.badRequest("邀请已被使用");
        }
        if (inv.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.badRequest("邀请链接已过期");
        }
        if (userRepo.existsByEmail(inv.getEmail())) {
            throw ApiException.conflict("该邮箱已被注册");
        }

        User user = new User();
        user.setEmail(inv.getEmail());
        user.setName(req.name());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setRole(inv.getRoleInTeam());
        user.setTeamId(inv.getTeamId());
        user = userRepo.save(user);

        TeamMember member = new TeamMember();
        member.setTeamId(inv.getTeamId());
        member.setUserId(user.getId());
        member.setRoleInTeam(inv.getRoleInTeam());
        teamMemberRepo.save(member);

        inv.setAccepted(true);
        inv.setAcceptedAt(Instant.now());
        invitationRepo.save(inv);

        auditService.log("INVITATION_ACCEPT", "User", user.getId(),
                "team=" + inv.getTeamId() + " role=" + inv.getRoleInTeam());
        return authService.issueTokensFor(user, http);
    }

    public boolean isExpired(Invitation inv) {
        return inv.getExpiresAt().isBefore(Instant.now());
    }
}
