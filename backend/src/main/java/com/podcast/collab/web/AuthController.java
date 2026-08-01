package com.podcast.collab.web;

import com.podcast.collab.dto.AuthDtos.*;
import com.podcast.collab.security.CurrentUser;
import com.podcast.collab.security.SecurityUtils;
import com.podcast.collab.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
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
        return authService.refresh(req, http);
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(@Valid @RequestBody RefreshRequest req) {
        authService.logout(req.refreshToken(), SecurityUtils.currentUser());
        return Map.of("success", true);
    }

    @PostMapping("/password/forgot")
    public Map<String, Object> forgot(@Valid @RequestBody ForgotPasswordRequest req) {
        authService.forgotPassword(req);
        return Map.of("success", true);
    }

    @PostMapping("/password/reset")
    public Map<String, Object> reset(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req);
        return Map.of("success", true);
    }

    @PostMapping("/invitations/accept")
    public TokenResponse acceptInvite(@Valid @RequestBody AcceptInviteRequest req, HttpServletRequest http) {
        return authService.acceptInvite(req, http);
    }

    @GetMapping("/me")
    public UserResponse me() {
        CurrentUser cu = SecurityUtils.requireUser();
        return new UserResponse(cu.id(), cu.email(), cu.name(), cu.teamId(),
                cu.teamRole() != null ? cu.teamRole().name() : null);
    }

    @PostMapping("/switch-team/{teamId}")
    public UserResponse switchTeam(@PathVariable Long teamId) {
        return authService.switchTeam(teamId, SecurityUtils.requireUser());
    }
}
