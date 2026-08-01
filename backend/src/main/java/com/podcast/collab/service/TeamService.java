package com.podcast.collab.service;

import com.podcast.collab.common.ApiException;
import com.podcast.collab.common.ErrorCode;
import com.podcast.collab.config.AppProperties;
import com.podcast.collab.domain.*;
import com.podcast.collab.dto.TeamDtos.*;
import com.podcast.collab.repo.*;
import com.podcast.collab.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TeamService {

    private final TeamRepository teamRepo;
    private final TeamMemberRepository memberRepo;
    private final UserRepository userRepo;
    private final InvitationRepository invitationRepo;
    private final AppProperties props;
    private final AuditService auditService;
    private final SecureRandom random = new SecureRandom();

    public TeamService(TeamRepository teamRepo, TeamMemberRepository memberRepo, UserRepository userRepo,
                       InvitationRepository invitationRepo, AppProperties props, AuditService auditService) {
        this.teamRepo = teamRepo;
        this.memberRepo = memberRepo;
        this.userRepo = userRepo;
        this.invitationRepo = invitationRepo;
        this.props = props;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<TeamResponse> myTeams(CurrentUser current) {
        List<TeamMember> memberships = memberRepo.findByUserId(current.id());
        Map<Long, Team> teamMap = teamRepo.findAllById(
                        memberships.stream().map(TeamMember::getTeamId).toList())
                .stream().collect(Collectors.toMap(Team::getId, Function.identity()));
        return memberships.stream()
                .map(m -> {
                    Team t = teamMap.get(m.getTeamId());
                    return new TeamResponse(t.getId(), t.getName(), t.getCreatedBy(), t.getCreatedAt(),
                            m.getRoleInTeam().name());
                })
                .toList();
    }

    @Transactional
    public TeamResponse updateTeam(Long teamId, UpdateTeamRequest req, CurrentUser current) {
        requireAdmin(teamId, current);
        Team team = teamRepo.findById(teamId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        team.setName(req.name());
        teamRepo.save(team);
        auditService.log(current, "TEAM_UPDATE", "Team", teamId, Map.of("name", req.name()));
        return toResponse(team, current);
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers(Long teamId, CurrentUser current) {
        requireMember(teamId, current);
        List<TeamMember> members = memberRepo.findByTeamId(teamId);
        Map<Long, User> users = userRepo.findAllById(members.stream().map(TeamMember::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return members.stream()
                .map(m -> {
                    User u = users.get(m.getUserId());
                    return new MemberResponse(u.getId(), u.getEmail(), u.getName(),
                            m.getRoleInTeam(), m.getJoinedAt());
                })
                .toList();
    }

    @Transactional
    public InvitationResponse invite(Long teamId, InviteRequest req, CurrentUser current) {
        requireAdmin(teamId, current);
        if (req.role() == Enums.TeamRole.ADMIN && !current.isAdmin()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Only admins can invite admins");
        }
        String token = randomToken(32);
        Invitation inv = new Invitation();
        inv.setTeamId(teamId);
        inv.setEmail(req.email().toLowerCase());
        inv.setRoleInTeam(req.role());
        inv.setToken(token);
        inv.setInvitedBy(current.id());
        inv.setExpiresAt(Instant.now().plus(props.getInvite().getTtlHours(), ChronoUnit.HOURS));
        invitationRepo.save(inv);
        auditService.log(current, "MEMBER_INVITE", "Invitation", inv.getId(),
                Map.of("email", inv.getEmail(), "role", req.role().name()));
        return new InvitationResponse(inv.getId(), inv.getEmail(), inv.getRoleInTeam(), inv.getExpiresAt(),
                false, "/invite/" + token);
    }

    @Transactional(readOnly = true)
    public InvitationResponse getInvitation(String token) {
        Invitation inv = invitationRepo.findByToken(token)
                .orElseThrow(() -> new ApiException(ErrorCode.INVITE_EXPIRED, "Invitation not found"));
        if (inv.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(ErrorCode.INVITE_EXPIRED);
        }
        Team team = teamRepo.findById(inv.getTeamId()).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        return new InvitationResponse(inv.getId(), inv.getEmail(), inv.getRoleInTeam(), inv.getExpiresAt(),
                inv.getAcceptedAt() != null, null);
    }

    @Transactional(readOnly = true)
    public List<InvitationResponse> listInvitations(Long teamId, CurrentUser current) {
        requireAdmin(teamId, current);
        return invitationRepo.findByTeamIdOrderByCreatedAtDesc(teamId).stream()
                .map(i -> new InvitationResponse(i.getId(), i.getEmail(), i.getRoleInTeam(), i.getExpiresAt(),
                        i.getAcceptedAt() != null, i.getAcceptedAt() == null ? "/invite/" + i.getToken() : null))
                .toList();
    }

    @Transactional
    public MemberResponse updateRole(Long teamId, Long userId, UpdateRoleRequest req, CurrentUser current) {
        requireAdmin(teamId, current);
        TeamMember member = memberRepo.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Member not found"));
        if (member.getUserId().equals(current.id())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "You cannot change your own role");
        }
        member.setRoleInTeam(req.role());
        memberRepo.save(member);
        auditService.log(current, "MEMBER_ROLE_UPDATE", "TeamMember", member.getId(),
                Map.of("userId", userId, "role", req.role().name()));
        User u = userRepo.findById(userId).orElseThrow();
        return new MemberResponse(u.getId(), u.getEmail(), u.getName(), member.getRoleInTeam(), member.getJoinedAt());
    }

    @Transactional
    public void removeMember(Long teamId, Long userId, CurrentUser current) {
        requireAdmin(teamId, current);
        if (userId.equals(current.id())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Use account settings to leave the team");
        }
        memberRepo.deleteByTeamIdAndUserId(teamId, userId);
        auditService.log(current, "MEMBER_REMOVE", "TeamMember", userId, null);
    }

    private TeamMember requireAdmin(Long teamId, CurrentUser current) {
        TeamMember m = requireMember(teamId, current);
        if (m.getRoleInTeam() != Enums.TeamRole.ADMIN) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Admin role required");
        }
        return m;
    }

    private TeamMember requireMember(Long teamId, CurrentUser current) {
        return memberRepo.findByTeamIdAndUserId(teamId, current.id())
                .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN, "Not a member of this team"));
    }

    private TeamResponse toResponse(Team team, CurrentUser current) {
        TeamMember m = memberRepo.findByTeamIdAndUserId(team.getId(), current.id()).orElse(null);
        return new TeamResponse(team.getId(), team.getName(), team.getCreatedBy(), team.getCreatedAt(),
                m != null ? m.getRoleInTeam().name() : null);
    }

    private String randomToken(int bytes) {
        byte[] buf = new byte[bytes];
        random.nextBytes(buf);
        return HexFormat.of().formatHex(buf);
    }
}
