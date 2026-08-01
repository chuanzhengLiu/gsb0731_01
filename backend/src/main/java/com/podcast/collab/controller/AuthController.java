package com.podcast.collab.controller;

import com.podcast.collab.dto.Dtos.*;
import com.podcast.collab.entity.Team;
import com.podcast.collab.entity.TeamInvite;
import com.podcast.collab.entity.User;
import com.podcast.collab.entity.UserSession;
import com.podcast.collab.repository.TeamRepository;
import com.podcast.collab.security.SecurityUtils;
import com.podcast.collab.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final TeamRepository teamRepository;
    private final com.podcast.collab.security.ClientIpResolver clientIpResolver;

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest req, HttpServletRequest http) {
        var tokens = authService.register(req.email(), req.password(), req.name(), req.teamName(),
                clientIp(http), http.getHeader("User-Agent"));
        return toAuthResponse(tokens);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        var tokens = authService.login(req.email(), req.password(),
                clientIp(http), http.getHeader("User-Agent"));
        return toAuthResponse(tokens);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest req, HttpServletRequest http) {
        var tokens = authService.refresh(req.refreshToken(), clientIp(http), http.getHeader("User-Agent"));
        return toAuthResponse(tokens);
    }

    @PostMapping("/logout")
    public Map<String, String> logout(@Valid @RequestBody RefreshRequest req) {
        authService.logout(req.refreshToken());
        return Map.of("message", "已退出登录");
    }

    @PostMapping("/forgot-password")
    public Map<String, String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        authService.requestPasswordReset(req.email());
        // 统一话术：不泄露邮箱是否注册，重置链接仅通过邮件下发
        return Map.of("message", "如果该邮箱已注册，重置链接已发送至邮箱（30分钟内有效）");
    }

    @PostMapping("/reset-password")
    public Map<String, String> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req.token(), req.newPassword());
        return Map.of("message", "密码重置成功，请重新登录");
    }

    @GetMapping("/invite-info")
    public Map<String, Object> inviteInfo(@RequestParam String token) {
        TeamInvite invite = authService.inviteInfo(token);
        Team team = teamRepository.findById(invite.getTeamId()).orElse(null);
        return Map.of(
                "email", invite.getEmail(),
                "role", invite.getRoleInTeam().name(),
                "teamName", team != null ? team.getName() : "",
                "expiresAt", invite.getExpiresAt().toString());
    }

    @PostMapping("/accept-invite")
    public AuthResponse acceptInvite(@Valid @RequestBody AcceptInviteRequest req, HttpServletRequest http) {
        var tokens = authService.acceptInvite(req.token(), req.name(), req.password(),
                clientIp(http), http.getHeader("User-Agent"));
        return toAuthResponse(tokens);
    }

    @GetMapping("/me")
    public UserInfo me() {
        return toUserInfo(authService.getUser(SecurityUtils.currentUserId()));
    }

    @GetMapping("/sessions")
    public List<SessionInfo> sessions() {
        Long userId = SecurityUtils.currentUserId();
        return authService.activeSessions(userId).stream()
                .map(s -> new SessionInfo(s.getId(), s.getIpAddress(), s.getUserAgent(),
                        s.getCreatedAt(), s.getExpiresAt(), false))
                .toList();
    }

    @DeleteMapping("/sessions/{id}")
    public Map<String, String> revokeSession(@PathVariable Long id) {
        authService.revokeSession(SecurityUtils.currentUserId(), id);
        return Map.of("message", "该会话已强制下线");
    }

    private AuthResponse toAuthResponse(AuthService.AuthTokens tokens) {
        return new AuthResponse(tokens.accessToken(), tokens.refreshToken(), toUserInfo(tokens.user()));
    }

    private UserInfo toUserInfo(User user) {
        String teamName = user.getTeamId() == null ? null
                : teamRepository.findById(user.getTeamId()).map(Team::getName).orElse(null);
        return new UserInfo(user.getId(), user.getEmail(), user.getName(),
                user.getRole().name(), user.getTeamId(), teamName);
    }

    private String clientIp(HttpServletRequest request) {
        return clientIpResolver.resolve(request);
    }
}
