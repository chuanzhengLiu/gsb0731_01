package com.podcast.collab.service;

import com.podcast.collab.common.ApiException;
import com.podcast.collab.common.ErrorCode;
import com.podcast.collab.config.AppProperties;
import com.podcast.collab.domain.*;
import com.podcast.collab.dto.AuthDtos.*;
import com.podcast.collab.repo.*;
import com.podcast.collab.security.CurrentUser;
import com.podcast.collab.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

@Service
public class AuthService {

    private final UserRepository userRepo;
    private final TeamRepository teamRepo;
    private final TeamMemberRepository memberRepo;
    private final RefreshTokenRepository refreshRepo;
    private final PasswordResetRepository passwordResetRepo;
    private final InvitationRepository invitationRepo;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final JwtService jwtService;
    private final AppProperties props;
    private final AuditService auditService;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository userRepo, TeamRepository teamRepo, TeamMemberRepository memberRepo,
                       RefreshTokenRepository refreshRepo, PasswordResetRepository passwordResetRepo,
                       InvitationRepository invitationRepo, PasswordEncoder passwordEncoder,
                       PasswordPolicy passwordPolicy, JwtService jwtService, AppProperties props,
                       AuditService auditService) {
        this.userRepo = userRepo;
        this.teamRepo = teamRepo;
        this.memberRepo = memberRepo;
        this.refreshRepo = refreshRepo;
        this.passwordResetRepo = passwordResetRepo;
        this.invitationRepo = invitationRepo;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.jwtService = jwtService;
        this.props = props;
        this.auditService = auditService;
    }

    @Transactional
    public TokenResponse register(RegisterRequest req, HttpServletRequest http) {
        if (userRepo.existsByEmail(req.email().toLowerCase())) {
            throw new ApiException(ErrorCode.CONFLICT, "Email already registered");
        }
        passwordPolicy.validate(req.password());

        User user = new User();
        user.setEmail(req.email().toLowerCase());
        user.setName(req.name());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setRole(Enums.SystemRole.USER);
        userRepo.save(user);

        String teamName = (req.teamName() == null || req.teamName().isBlank())
                ? req.name() + " 的团队" : req.teamName();
        Team team = new Team();
        team.setName(teamName);
        team.setCreatedBy(user.getId());
        teamRepo.save(team);

        TeamMember member = new TeamMember();
        member.setTeamId(team.getId());
        member.setUserId(user.getId());
        member.setRoleInTeam(Enums.TeamRole.ADMIN);
        member.setJoinedAt(Instant.now());
        memberRepo.save(member);

        user.setActiveTeamId(team.getId());
        userRepo.save(user);

        auditService.log(new CurrentUser(user.getId(), user.getEmail(), user.getName(), team.getId(), Enums.TeamRole.ADMIN),
                "USER_REGISTER", "User", user.getId(), null);
        return issueTokens(user, http);
    }

    @Transactional
    public TokenResponse login(LoginRequest req, HttpServletRequest http) {
        User user = userRepo.findByEmail(req.email().toLowerCase())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS));
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        return issueTokens(user, http);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest req, HttpServletRequest http) {
        RefreshToken stored;
        try {
            var claims = jwtService.parse(req.refreshToken());
            if (!"refresh".equals(claims.get("type"))) {
                throw new ApiException(ErrorCode.TOKEN_INVALID);
            }
            stored = refreshRepo.findByTokenHash(hash(req.refreshToken()))
                    .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));
            if (stored.isRevoked() || stored.getExpiresAt().isBefore(Instant.now())) {
                throw new ApiException(ErrorCode.TOKEN_EXPIRED);
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(ErrorCode.TOKEN_INVALID);
        }
        User user = userRepo.findById(stored.getUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));
        stored.setRevoked(true);
        refreshRepo.save(stored);
        return issueTokens(user, http);
    }

    @Transactional
    public void logout(String refreshToken, CurrentUser user) {
        try {
            refreshRepo.findByTokenHash(hash(refreshToken)).ifPresent(rt -> {
                rt.setRevoked(true);
                refreshRepo.save(rt);
            });
        } catch (Exception ignored) {}
        if (user != null) {
            auditService.log(user, "USER_LOGOUT", "User", user.id(), null);
        }
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest req) {
        userRepo.findByEmail(req.email().toLowerCase()).ifPresent(user -> {
            String token = randomToken(48);
            PasswordReset pr = new PasswordReset();
            pr.setUserId(user.getId());
            pr.setTokenHash(hash(token));
            pr.setExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES));
            pr.setCreatedAt(Instant.now());
            passwordResetRepo.save(pr);
            // In production, send email with reset link. For dev, log token (not exposed to caller).
            auditService.log(new CurrentUser(user.getId(), user.getEmail(), user.getName(), null, null),
                    "PASSWORD_RESET_REQUESTED", "User", user.getId(), null);
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        PasswordReset pr = passwordResetRepo.findByTokenHash(hash(req.token()))
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));
        if (pr.getUsedAt() != null || pr.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(ErrorCode.TOKEN_EXPIRED, "Reset link expired");
        }
        passwordPolicy.validate(req.password());
        User user = userRepo.findById(pr.getUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        userRepo.save(user);
        pr.setUsedAt(Instant.now());
        passwordResetRepo.save(pr);
        refreshRepo.revokeAllForUser(user.getId());
        auditService.log(new CurrentUser(user.getId(), user.getEmail(), user.getName(), null, null),
                "PASSWORD_RESET", "User", user.getId(), null);
    }

    @Transactional
    public TokenResponse acceptInvite(AcceptInviteRequest req, HttpServletRequest http) {
        Invitation inv = invitationRepo.findByToken(req.token())
                .orElseThrow(() -> new ApiException(ErrorCode.INVITE_EXPIRED, "Invitation not found"));
        if (inv.getAcceptedAt() != null || inv.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(ErrorCode.INVITE_EXPIRED);
        }
        if (userRepo.existsByEmail(inv.getEmail().toLowerCase())) {
            throw new ApiException(ErrorCode.CONFLICT, "An account with this email already exists");
        }
        passwordPolicy.validate(req.password());

        User user = new User();
        user.setEmail(inv.getEmail().toLowerCase());
        user.setName(req.name());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setRole(Enums.SystemRole.USER);
        user.setActiveTeamId(inv.getTeamId());
        userRepo.save(user);

        TeamMember member = new TeamMember();
        member.setTeamId(inv.getTeamId());
        member.setUserId(user.getId());
        member.setRoleInTeam(inv.getRoleInTeam());
        member.setJoinedAt(Instant.now());
        memberRepo.save(member);

        inv.setAcceptedAt(Instant.now());
        invitationRepo.save(inv);

        auditService.log(new CurrentUser(user.getId(), user.getEmail(), user.getName(), inv.getTeamId(), inv.getRoleInTeam()),
                "INVITE_ACCEPTED", "Invitation", inv.getId(), null);
        return issueTokens(user, http);
    }

    @Transactional
    public UserResponse switchTeam(Long teamId, CurrentUser current) {
        if (!memberRepo.existsByTeamIdAndUserId(teamId, current.id())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "You are not a member of this team");
        }
        User user = userRepo.findById(current.id()).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        user.setActiveTeamId(teamId);
        userRepo.save(user);
        TeamMember m = memberRepo.findByTeamIdAndUserId(teamId, user.getId()).orElseThrow();
        auditService.log(current, "TEAM_SWITCH", "Team", teamId, null);
        return toUserResponse(user, m.getRoleInTeam() != null ? m.getRoleInTeam().name() : null);
    }

    public TokenResponse issueTokens(User user, HttpServletRequest http) {
        TeamMember member = user.getActiveTeamId() != null
                ? memberRepo.findByTeamIdAndUserId(user.getActiveTeamId(), user.getId()).orElse(null)
                : null;
        String role = member != null ? member.getRoleInTeam().name() : null;
        String access = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getActiveTeamId(), role);
        String refresh = jwtService.generateRefreshToken(user.getId());

        RefreshToken rt = new RefreshToken();
        rt.setUserId(user.getId());
        rt.setTokenHash(hash(refresh));
        rt.setExpiresAt(Instant.now().plus(props.getJwt().getRefreshTokenTtl()));
        rt.setUserAgent(http != null ? truncate(http.getHeader("User-Agent"), 255) : null);
        rt.setIpAddress(clientIp(http));
        rt.setCreatedAt(Instant.now());
        refreshRepo.save(rt);

        return new TokenResponse(access, refresh, "Bearer",
                props.getJwt().getAccessTokenTtl().toSeconds(),
                toUserResponse(user, role));
    }

    public UserResponse toUserResponse(User user, String role) {
        return new UserResponse(user.getId(), user.getEmail(), user.getName(),
                user.getActiveTeamId(), role);
    }

    private String hash(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String randomToken(int bytes) {
        byte[] buf = new byte[bytes];
        random.nextBytes(buf);
        return HexFormat.of().formatHex(buf);
    }

    private String clientIp(HttpServletRequest req) {
        if (req == null) return null;
        String ip = req.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) return ip.split(",")[0].trim();
        return req.getRemoteAddr();
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
