package com.podcast.collab.service;

import com.podcast.collab.dto.team.*;
import com.podcast.collab.entity.Team;
import com.podcast.collab.entity.TeamInvitation;
import com.podcast.collab.entity.TeamMember;
import com.podcast.collab.entity.User;
import com.podcast.collab.entity.enums.SystemRole;
import com.podcast.collab.entity.enums.TeamRole;
import com.podcast.collab.exception.AccessDeniedException;
import com.podcast.collab.exception.BadRequestException;
import com.podcast.collab.exception.ResourceNotFoundException;
import com.podcast.collab.repository.TeamInvitationRepository;
import com.podcast.collab.repository.TeamMemberRepository;
import com.podcast.collab.repository.TeamRepository;
import com.podcast.collab.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class TeamService {

    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[a-zA-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).{10,}$");

    private static final long INVITATION_EXPIRY_HOURS = 24;

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamInvitationRepository teamInvitationRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final BCryptPasswordEncoder passwordEncoder;

    public TeamService(TeamRepository teamRepository,
                       TeamMemberRepository teamMemberRepository,
                       TeamInvitationRepository teamInvitationRepository,
                       UserRepository userRepository,
                       AuditService auditService) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.teamInvitationRepository = teamInvitationRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.passwordEncoder = new BCryptPasswordEncoder(12);
    }

    @Transactional
    public TeamResponse createTeam(CreateTeamRequest request, Long userId) {
        Team team = Team.builder()
                .name(request.getName())
                .createdBy(userId)
                .build();
        team = teamRepository.save(team);

        TeamMember member = TeamMember.builder()
                .teamId(team.getId())
                .userId(userId)
                .roleInTeam(TeamRole.ADMIN)
                .build();
        teamMemberRepository.save(member);

        auditService.log(userId, "CREATE_TEAM", "Team", team.getId(),
                "Team created: " + team.getName());

        return mapToTeamResponse(team, TeamRole.ADMIN);
    }

    @Transactional(readOnly = true)
    public List<TeamResponse> getUserTeams(Long userId) {
        List<Team> teams = teamRepository.findTeamsByUserId(userId);
        return teams.stream()
                .map(team -> {
                    TeamMember membership = teamMemberRepository
                            .findByTeamIdAndUserId(team.getId(), userId)
                            .orElse(null);
                    TeamRole role = membership != null ? membership.getRoleInTeam() : null;
                    return mapToTeamResponse(team, role);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public TeamResponse getTeamById(Long teamId, Long userId) {
        Team team = findTeamOrThrow(teamId);
        TeamMember membership = verifyMembership(teamId, userId);
        return mapToTeamResponse(team, membership.getRoleInTeam());
    }

    @Transactional
    public InvitationResponse inviteMember(Long teamId, InviteMemberRequest request, Long userId) {
        Team team = findTeamOrThrow(teamId);
        verifyTeamAdmin(teamId, userId);

        if (teamMemberRepository.existsByTeamIdAndUserId(teamId,
                userRepository.findByEmail(request.getEmail())
                        .map(User::getId)
                        .orElse(-1L))) {
            throw new BadRequestException("User is already a member of this team");
        }

        String token = UUID.randomUUID().toString();

        TeamInvitation invitation = TeamInvitation.builder()
                .teamId(teamId)
                .email(request.getEmail())
                .roleInTeam(request.getRoleInTeam())
                .token(token)
                .invitedBy(userId)
                .expiresAt(LocalDateTime.now().plusHours(INVITATION_EXPIRY_HOURS))
                .build();
        invitation = teamInvitationRepository.save(invitation);

        auditService.log(userId, "INVITE_MEMBER", "TeamInvitation", invitation.getId(),
                "Invited " + request.getEmail() + " to team " + teamId + " as " + request.getRoleInTeam());

        return mapToInvitationResponse(invitation);
    }

    @Transactional(readOnly = true)
    public List<TeamMemberResponse> getTeamMembers(Long teamId, Long userId) {
        verifyMembership(teamId, userId);

        List<TeamMember> members = teamMemberRepository.findByTeamId(teamId);
        return members.stream()
                .map(member -> {
                    User user = userRepository.findById(member.getUserId())
                            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
                    return mapToMemberResponse(member, user);
                })
                .toList();
    }

    @Transactional
    public void removeMember(Long teamId, Long memberId, Long userId) {
        verifyTeamAdmin(teamId, userId);

        TeamMember member = teamMemberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Team member not found"));

        if (!member.getTeamId().equals(teamId)) {
            throw new BadRequestException("Member does not belong to this team");
        }

        teamMemberRepository.delete(member);

        auditService.log(userId, "REMOVE_MEMBER", "TeamMember", memberId,
                "Removed member " + member.getUserId() + " from team " + teamId);
    }

    @Transactional
    public TeamMemberResponse updateMemberRole(Long teamId, Long memberId,
                                                UpdateMemberRoleRequest request, Long userId) {
        verifyTeamAdmin(teamId, userId);

        TeamMember member = teamMemberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Team member not found"));

        if (!member.getTeamId().equals(teamId)) {
            throw new BadRequestException("Member does not belong to this team");
        }

        member.setRoleInTeam(request.getRoleInTeam());
        member = teamMemberRepository.save(member);

        User user = userRepository.findById(member.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        auditService.log(userId, "UPDATE_MEMBER_ROLE", "TeamMember", memberId,
                "Updated role to " + request.getRoleInTeam() + " for member " + member.getUserId());

        return mapToMemberResponse(member, user);
    }

    @Transactional
    public TeamMemberResponse acceptInvitation(AcceptInviteRequest request, Long currentUserId) {
        TeamInvitation invitation = teamInvitationRepository.findByToken(request.getToken())
                .orElseThrow(() -> new BadRequestException("Invalid invitation token"));

        if (invitation.getAcceptedAt() != null) {
            throw new BadRequestException("Invitation has already been accepted");
        }

        if (invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Invitation has expired");
        }

        User user = userRepository.findByEmail(invitation.getEmail()).orElse(null);

        if (user == null) {
            if (request.getName() == null || request.getName().isBlank()
                    || request.getPassword() == null || request.getPassword().isBlank()) {
                throw new BadRequestException("Name and password are required for new users");
            }

            if (!PASSWORD_PATTERN.matcher(request.getPassword()).matches()) {
                throw new BadRequestException(
                        "Password must be at least 10 characters and contain at least one letter, one digit, and one special character");
            }

            user = User.builder()
                    .email(invitation.getEmail())
                    .passwordHash(passwordEncoder.encode(request.getPassword()))
                    .name(request.getName())
                    .systemRole(SystemRole.USER)
                    .build();
            user = userRepository.save(user);
        }

        if (teamMemberRepository.existsByTeamIdAndUserId(invitation.getTeamId(), user.getId())) {
            throw new BadRequestException("User is already a member of this team");
        }

        TeamMember member = TeamMember.builder()
                .teamId(invitation.getTeamId())
                .userId(user.getId())
                .roleInTeam(invitation.getRoleInTeam())
                .build();
        member = teamMemberRepository.save(member);

        invitation.setAcceptedAt(LocalDateTime.now());
        teamInvitationRepository.save(invitation);

        auditService.log(currentUserId != null ? currentUserId : user.getId(),
                "ACCEPT_INVITATION", "TeamMember", member.getId(),
                "User " + user.getId() + " accepted invitation to team " + invitation.getTeamId());

        return mapToMemberResponse(member, user);
    }

    @Transactional(readOnly = true)
    public List<InvitationResponse> getPendingInvitations(String email) {
        return teamInvitationRepository.findByEmailAndAcceptedAtIsNull(email).stream()
                .filter(inv -> inv.getExpiresAt().isAfter(LocalDateTime.now()))
                .map(this::mapToInvitationResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InvitationResponse> getInvitationsByEmail(String email) {
        return teamInvitationRepository.findByEmailAndAcceptedAtIsNull(email).stream()
                .map(this::mapToInvitationResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InvitationResponse> getTeamInvitations(Long teamId, Long userId) {
        verifyMembership(teamId, userId);
        return teamInvitationRepository.findByTeamId(teamId).stream()
                .map(this::mapToInvitationResponse)
                .toList();
    }

    private Team findTeamOrThrow(Long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found"));
    }

    private TeamMember verifyMembership(Long teamId, Long userId) {
        return teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> new AccessDeniedException("You are not a member of this team"));
    }

    private void verifyTeamAdmin(Long teamId, Long userId) {
        TeamMember membership = verifyMembership(teamId, userId);
        if (membership.getRoleInTeam() != TeamRole.ADMIN) {
            throw new AccessDeniedException("Only team admins can perform this action");
        }
    }

    private TeamResponse mapToTeamResponse(Team team, TeamRole currentUserRole) {
        return TeamResponse.builder()
                .id(team.getId())
                .name(team.getName())
                .createdBy(team.getCreatedBy())
                .createdAt(team.getCreatedAt())
                .currentUserRole(currentUserRole)
                .build();
    }

    private TeamMemberResponse mapToMemberResponse(TeamMember member, User user) {
        return TeamMemberResponse.builder()
                .id(member.getId())
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .roleInTeam(member.getRoleInTeam())
                .joinedAt(member.getJoinedAt())
                .avatarUrl(user.getAvatarUrl())
                .build();
    }

    private InvitationResponse mapToInvitationResponse(TeamInvitation invitation) {
        return InvitationResponse.builder()
                .id(invitation.getId())
                .teamId(invitation.getTeamId())
                .email(invitation.getEmail())
                .roleInTeam(invitation.getRoleInTeam())
                .token(invitation.getToken())
                .expiresAt(invitation.getExpiresAt())
                .acceptedAt(invitation.getAcceptedAt())
                .build();
    }
}
