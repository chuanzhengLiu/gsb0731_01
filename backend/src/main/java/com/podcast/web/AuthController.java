package com.podcast.web;

import com.podcast.domain.RefreshToken;
import com.podcast.security.SecurityUtils;
import com.podcast.service.AuthService;
import com.podcast.web.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public TokenResponse register(@Valid @RequestBody RegisterRequest req, HttpServletRequest http) {
        return authService.register(req, http);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return authService.login(req, http);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest req, HttpServletRequest http) {
        return authService.refresh(req.refreshToken(), http);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshRequest req) {
        authService.logout(req != null ? req.refreshToken() : null);
        return ResponseEntity.noContent().build();
    }

    /**
     * Start password recovery (README §3.2). Always returns 204 regardless of
     * whether the email exists, to avoid leaking registered accounts.
     */
    @PostMapping("/password/forgot")
    public ResponseEntity<Void> forgotPassword(
            @Valid @RequestBody PasswordResetDtos.ForgotPasswordRequest req) {
        authService.requestPasswordReset(req.email());
        return ResponseEntity.noContent().build();
    }

    /** Complete password recovery with the emailed token (30min valid). */
    @PostMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(
            @Valid @RequestBody PasswordResetDtos.ResetPasswordRequest req) {
        authService.resetPassword(req.token(), req.password());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        var u = SecurityUtils.currentUser();
        return Map.of(
                "id", u.getUserId(),
                "email", u.getEmail(),
                "role", u.getRole(),
                "teamId", u.getTeamId());
    }

    /** Active sessions of the current user (README §3.2 查看活跃会话). */
    @GetMapping("/sessions")
    public List<Map<String, Object>> sessions() {
        Long userId = SecurityUtils.currentUserId();
        return authService.activeSessions(userId).stream().map(this::sessionView).toList();
    }

    /** Force-logout all sessions of the current user (强制下线). */
    @PostMapping("/sessions/revoke-all")
    public ResponseEntity<Void> revokeAll() {
        authService.revokeAllSessions(SecurityUtils.currentUserId());
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> sessionView(RefreshToken rt) {
        return Map.of(
                "id", rt.getId(),
                "userAgent", rt.getUserAgent() == null ? "" : rt.getUserAgent(),
                "ipAddress", rt.getIpAddress() == null ? "" : rt.getIpAddress(),
                "createdAt", rt.getCreatedAt().toString(),
                "expiresAt", rt.getExpiresAt().toString(),
                "expired", rt.getExpiresAt().isBefore(Instant.now()));
    }
}
