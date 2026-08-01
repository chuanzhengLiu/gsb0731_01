package com.podcast.service;

import com.podcast.config.AppProperties;
import com.podcast.domain.*;
import com.podcast.repository.*;
import com.podcast.security.JwtService;
import com.podcast.security.TokenUtil;
import com.podcast.web.ApiException;
import com.podcast.web.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class AuthService {

    private final UserRepository userRepo;
    private final TeamRepository teamRepo;
    private final TeamMemberRepository teamMemberRepo;
    private final RefreshTokenRepository refreshRepo;
    private final PasswordResetTokenRepository resetRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AppProperties props;
    private final AuditService auditService;
    private final EmailService emailService;

    public AuthService(UserRepository userRepo, TeamRepository teamRepo,
                       TeamMemberRepository teamMemberRepo, RefreshTokenRepository refreshRepo,
                       PasswordResetTokenRepository resetRepo,
                       PasswordEncoder passwordEncoder, JwtService jwtService,
                       AppProperties props, AuditService auditService, EmailService emailService) {
        this.userRepo = userRepo;
        this.teamRepo = teamRepo;
        this.teamMemberRepo = teamMemberRepo;
        this.refreshRepo = refreshRepo;
        this.resetRepo = resetRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.props = props;
        this.auditService = auditService;
        this.emailService = emailService;
    }

    @Transactional
    public TokenResponse register(RegisterRequest req, HttpServletRequest http) {
        if (userRepo.existsByEmail(req.email())) {
            throw ApiException.conflict("该邮箱已被注册");
        }
        // Create team; registering user becomes ADMIN.
        Team team = new Team();
        team.setName(req.teamName());
        team = teamRepo.save(team);

        User user = new User();
        user.setEmail(req.email());
        user.setName(req.name());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setRole(Role.ADMIN);
        user.setTeamId(team.getId());
        user = userRepo.save(user);

        team.setCreatedBy(user.getId());
        teamRepo.save(team);

        TeamMember member = new TeamMember();
        member.setTeamId(team.getId());
        member.setUserId(user.getId());
        member.setRoleInTeam(Role.ADMIN);
        teamMemberRepo.save(member);

        auditService.log("USER_REGISTER", "User", user.getId(), "注册并创建团队 " + team.getName());
        return issueTokens(user, http);
    }

    @Transactional
    public TokenResponse login(LoginRequest req, HttpServletRequest http) {
        User user = userRepo.findByEmail(req.email())
                .orElseThrow(() -> ApiException.unauthorized("邮箱或密码错误"));
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized("邮箱或密码错误");
        }
        auditService.log("USER_LOGIN", "User", user.getId(), null);
        return issueTokens(user, http);
    }

    @Transactional
    public TokenResponse refresh(String rawRefreshToken, HttpServletRequest http) {
        String hash = TokenUtil.sha256(rawRefreshToken);
        RefreshToken stored = refreshRepo.findByTokenHash(hash)
                .orElseThrow(() -> ApiException.unauthorized("refresh token 无效"));
        if (stored.isRevoked() || stored.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.unauthorized("refresh token 已失效");
        }
        // Rotate: revoke the used token and issue a new pair.
        stored.setRevoked(true);
        refreshRepo.save(stored);

        User user = userRepo.findById(stored.getUserId())
                .orElseThrow(() -> ApiException.unauthorized("用户不存在"));
        return issueTokens(user, http);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null) return;
        refreshRepo.findByTokenHash(TokenUtil.sha256(rawRefreshToken)).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshRepo.save(rt);
        });
    }

    /** Force-logout all sessions of a user (README §3.2 强制下线). */
    @Transactional
    public void revokeAllSessions(Long userId) {
        refreshRepo.revokeAllForUser(userId);
        auditService.log("SESSION_REVOKE_ALL", "User", userId, "强制下线全部会话");
    }

    @Transactional(readOnly = true)
    public List<RefreshToken> activeSessions(Long userId) {
        return refreshRepo.findByUserIdAndRevokedFalse(userId);
    }

    /** Issues an access+refresh token pair for a freshly provisioned user (e.g. invitation accept). */
    @Transactional
    public TokenResponse issueTokensFor(User user, HttpServletRequest http) {
        return issueTokens(user, http);
    }

    private TokenResponse issueTokens(User user, HttpServletRequest http) {
        String access = jwtService.generateAccessToken(
                user.getId(), user.getEmail(), user.getRole().name(), user.getTeamId());

        String rawRefresh = TokenUtil.generateRawToken();
        RefreshToken rt = new RefreshToken();
        rt.setUserId(user.getId());
        rt.setTokenHash(TokenUtil.sha256(rawRefresh));
        rt.setExpiresAt(Instant.now().plusSeconds(props.getJwt().getRefreshTokenTtlSeconds()));
        if (http != null) {
            rt.setUserAgent(truncate(http.getHeader("User-Agent"), 255));
            rt.setIpAddress(clientIp(http));
        }
        refreshRepo.save(rt);

        return new TokenResponse(access, rawRefresh,
                props.getJwt().getAccessTokenTtlSeconds(), UserDto.from(user));
    }

    // ---------- Password recovery (README §3.2: 邮箱链接, 30 分钟有效) ----------

    /**
     * Starts password recovery. Always succeeds from the caller's point of
     * view so we never leak whether an email is registered; only if the user
     * exists is a reset token created and an email dispatched.
     */
    @Transactional
    public void requestPasswordReset(String email) {
        userRepo.findByEmail(email).ifPresent(user -> {
            // Invalidate outstanding tokens, then issue a fresh single-use one.
            resetRepo.invalidateAllForUser(user.getId());

            String rawToken = TokenUtil.generateRawToken();
            PasswordResetToken prt = new PasswordResetToken();
            prt.setUserId(user.getId());
            prt.setTokenHash(TokenUtil.sha256(rawToken));
            prt.setExpiresAt(Instant.now().plusSeconds(props.getPasswordReset().getTtlSeconds()));
            resetRepo.save(prt);

            emailService.sendPasswordReset(user.getEmail(), rawToken);
            auditService.log("PASSWORD_RESET_REQUEST", "User", user.getId(), null);
        });
    }

    /**
     * Completes password recovery: validates the token, updates the password
     * (BCrypt) and force-logs-out all existing sessions of that user.
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken prt = resetRepo.findByTokenHash(TokenUtil.sha256(rawToken))
                .orElseThrow(() -> ApiException.badRequest("重置链接无效"));
        if (prt.isUsed()) {
            throw ApiException.badRequest("重置链接已被使用");
        }
        if (prt.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.badRequest("重置链接已过期");
        }
        User user = userRepo.findById(prt.getUserId())
                .orElseThrow(() -> ApiException.badRequest("用户不存在"));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepo.save(user);

        prt.setUsed(true);
        resetRepo.save(prt);

        // Any active sessions become invalid after a password reset.
        refreshRepo.revokeAllForUser(user.getId());
        auditService.log("PASSWORD_RESET", "User", user.getId(), "密码已重置并强制下线全部会话");
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
