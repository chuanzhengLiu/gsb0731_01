package com.podcast.collab.service;

import com.podcast.collab.entity.*;
import com.podcast.collab.repository.*;
import com.podcast.collab.security.JwtService;
import com.podcast.collab.security.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamInviteRepository teamInviteRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserSessionRepository userSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final MailService mailService;

    @Value("${app.refresh-token-ttl-days}")
    private long refreshTtlDays;
    @Value("${app.invite-ttl-hours}")
    private long inviteTtlHours;
    @Value("${app.password-reset-ttl-minutes}")
    private long passwordResetTtlMinutes;
    @Value("${app.public-base-url}")
    private String publicBaseUrl;

    private static final SecureRandom RANDOM = new SecureRandom();
    /** 密码策略：最小10位，字母+数字+特殊字符 */
    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{10,}$");

    public record AuthTokens(String accessToken, String refreshToken, User user) {
    }

    @Transactional
    public AuthTokens register(String email, String password, String name, String teamName,
                               String ip, String userAgent) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("该邮箱已被注册");
        }
        validatePassword(password);

        Team team = new Team();
        team.setName(teamName);
        team = teamRepository.save(team);

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setName(name);
        user.setRole(Role.ADMIN);
        user.setTeamId(team.getId());
        user = userRepository.save(user);

        team.setCreatedBy(user.getId());
        teamRepository.save(team);

        TeamMember member = new TeamMember();
        member.setTeamId(team.getId());
        member.setUserId(user.getId());
        member.setRoleInTeam(Role.ADMIN);
        teamMemberRepository.save(member);

        auditService.log(user.getId(), team.getId(), "TEAM_CREATE", "team", team.getId(), "创建团队: " + teamName);
        return issueTokens(user, ip, userAgent);
    }

    public AuthTokens login(String email, String password, String ip, String userAgent) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("邮箱或密码错误"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new UnauthorizedException("邮箱或密码错误");
        }
        auditService.log(user.getId(), user.getTeamId(), "LOGIN", "user", user.getId(), "登录成功");
        return issueTokens(user, ip, userAgent);
    }

    public AuthTokens refresh(String refreshToken, String ip, String userAgent) {
        UserSession session = userSessionRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new UnauthorizedException("refresh_token 无效"));
        if (session.isRevoked() || session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new UnauthorizedException("refresh_token 已失效");
        }
        User user = userRepository.findById(session.getUserId())
                .orElseThrow(() -> new UnauthorizedException("用户不存在"));
        // 轮换 refresh token
        session.setRevoked(true);
        userSessionRepository.save(session);
        return issueTokens(user, ip, userAgent);
    }

    public void logout(String refreshToken) {
        userSessionRepository.findByRefreshToken(refreshToken).ifPresent(s -> {
            s.setRevoked(true);
            userSessionRepository.save(s);
        });
    }

    public User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    }

    public List<UserSession> activeSessions(Long userId) {
        return userSessionRepository.findByUserIdAndRevokedFalse(userId);
    }

    /** 强制下线指定会话 */
    public void revokeSession(Long userId, Long sessionId) {
        UserSession session = userSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        if (!session.getUserId().equals(userId)) {
            throw new UnauthorizedException("无权操作该会话");
        }
        session.setRevoked(true);
        userSessionRepository.save(session);
    }

    /**
     * 密码找回：无论邮箱是否注册都不泄露结果，token 只经邮件通道下发。
     * 邮箱不存在时静默返回（调用方返回统一话术）。
     */
    public void requestPasswordReset(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            PasswordResetToken token = new PasswordResetToken();
            token.setUserId(user.getId());
            token.setToken(randomToken());
            token.setExpiresAt(LocalDateTime.now().plusMinutes(passwordResetTtlMinutes)); // 30分钟有效
            passwordResetTokenRepository.save(token);
            String resetLink = publicBaseUrl + "/reset-password?token=" + token.getToken();
            mailService.send(email, "密码重置链接（30分钟内有效）",
                    "你好 " + user.getName() + "，请点击以下链接重置密码（30分钟内有效，若非本人操作请忽略）：\n"
                            + resetLink);
            auditService.log(user.getId(), user.getTeamId(), "PASSWORD_RESET_REQUEST", "user", user.getId(),
                    "发起密码找回");
        });
    }

    @Transactional
    public void resetPassword(String tokenValue, String newPassword) {
        PasswordResetToken token = passwordResetTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new IllegalArgumentException("重置链接无效"));
        if (token.isUsed() || token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("重置链接已过期");
        }
        validatePassword(newPassword);
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        token.setUsed(true);
        passwordResetTokenRepository.save(token);
        // 重置密码后强制所有会话下线
        userSessionRepository.findByUserIdAndRevokedFalse(user.getId())
                .forEach(s -> {
                    s.setRevoked(true);
                    userSessionRepository.save(s);
                });
        auditService.log(user.getId(), user.getTeamId(), "PASSWORD_RESET", "user", user.getId(), "密码重置");
    }

    /** 管理员邮箱邀请成员，链接24小时有效 */
    public String createInvite(Long teamId, String email, Role roleInTeam) {
        TeamInvite invite = new TeamInvite();
        invite.setTeamId(teamId);
        invite.setEmail(email);
        invite.setToken(randomToken());
        invite.setRoleInTeam(roleInTeam);
        invite.setExpiresAt(LocalDateTime.now().plusHours(inviteTtlHours));
        teamInviteRepository.save(invite);
        return invite.getToken();
    }

    public TeamInvite inviteInfo(String tokenValue) {
        return teamInviteRepository.findByToken(tokenValue)
                .filter(i -> !i.isUsed() && i.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new IllegalArgumentException("邀请链接无效或已过期"));
    }

    @Transactional
    public AuthTokens acceptInvite(String tokenValue, String name, String password, String ip, String userAgent) {
        TeamInvite invite = inviteInfo(tokenValue);
        if (userRepository.existsByEmail(invite.getEmail())) {
            throw new IllegalArgumentException("该邮箱已被注册");
        }
        validatePassword(password);
        User user = new User();
        user.setEmail(invite.getEmail());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setName(name);
        user.setRole(invite.getRoleInTeam());
        user.setTeamId(invite.getTeamId());
        user = userRepository.save(user);

        TeamMember member = new TeamMember();
        member.setTeamId(invite.getTeamId());
        member.setUserId(user.getId());
        member.setRoleInTeam(invite.getRoleInTeam());
        teamMemberRepository.save(member);

        invite.setUsed(true);
        teamInviteRepository.save(invite);
        auditService.log(user.getId(), invite.getTeamId(), "INVITE_ACCEPT", "user", user.getId(),
                "接受邀请加入团队，角色: " + invite.getRoleInTeam());
        return issueTokens(user, ip, userAgent);
    }

    private AuthTokens issueTokens(User user, String ip, String userAgent) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getTeamId(), user.getRole().name());
        String refreshToken = randomToken();
        UserSession session = new UserSession();
        session.setUserId(user.getId());
        session.setRefreshToken(refreshToken);
        session.setIpAddress(ip);
        session.setUserAgent(userAgent);
        session.setExpiresAt(LocalDateTime.now().plusDays(refreshTtlDays));
        userSessionRepository.save(session);
        return new AuthTokens(accessToken, refreshToken, user);
    }

    private void validatePassword(String password) {
        if (password == null || !PASSWORD_PATTERN.matcher(password).matches()) {
            throw new IllegalArgumentException("密码至少10位，且必须包含字母、数字和特殊字符");
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
