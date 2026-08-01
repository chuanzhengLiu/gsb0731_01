package com.podcast.collab.service;

import com.podcast.collab.dto.auth.*;
import com.podcast.collab.entity.PasswordResetToken;
import com.podcast.collab.entity.Team;
import com.podcast.collab.entity.TeamMember;
import com.podcast.collab.entity.User;
import com.podcast.collab.entity.enums.SystemRole;
import com.podcast.collab.entity.enums.TeamRole;
import com.podcast.collab.exception.BadRequestException;
import com.podcast.collab.exception.ResourceNotFoundException;
import com.podcast.collab.repository.PasswordResetTokenRepository;
import com.podcast.collab.repository.TeamMemberRepository;
import com.podcast.collab.repository.TeamRepository;
import com.podcast.collab.repository.UserRepository;
import com.podcast.collab.security.JwtTokenProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AuthService {

    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[a-zA-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).{10,}$");

    private static final long RESET_TOKEN_EXPIRY_MINUTES = 30;

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository,
                       TeamRepository teamRepository,
                       TeamMemberRepository teamMemberRepository,
                       PasswordResetTokenRepository passwordResetTokenRepository,
                       JwtTokenProvider jwtTokenProvider,
                       RefreshTokenService refreshTokenService,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
        this.passwordEncoder = new BCryptPasswordEncoder(12);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        validatePassword(request.getPassword());

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email is already registered");
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .systemRole(SystemRole.USER)
                .build();
        user = userRepository.save(user);

        String teamName = request.getTeamName();
        if (teamName == null || teamName.isBlank()) {
            teamName = request.getName() + "'s Team";
        }

        Team team = Team.builder()
                .name(teamName)
                .createdBy(user.getId())
                .build();
        team = teamRepository.save(team);

        TeamMember member = TeamMember.builder()
                .teamId(team.getId())
                .userId(user.getId())
                .roleInTeam(TeamRole.ADMIN)
                .build();
        teamMemberRepository.save(member);

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getName());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
        refreshTokenService.createRefreshToken(user.getId(), refreshToken);

        auditService.log(user.getId(), "REGISTER", "User", user.getId(), "User registered and default team created");

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .build();
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Invalid email or password");
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getName());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
        refreshTokenService.createRefreshToken(user.getId(), refreshToken);

        auditService.log(user.getId(), "LOGIN", "User", user.getId(), "User logged in");

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .build();
    }

    @Transactional
    public AuthResponse refreshToken(RefreshRequest request) {
        String token = request.getRefreshToken();

        if (!jwtTokenProvider.validateToken(token)) {
            throw new BadRequestException("Invalid refresh token");
        }

        refreshTokenService.validateRefreshToken(token);

        Long userId = jwtTokenProvider.getUserIdFromToken(token);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getName());

        auditService.log(user.getId(), "REFRESH_TOKEN", "User", user.getId(), "Access token refreshed");

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(token)
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .build();
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmail(request.getEmail()).ifPresent(user -> {
            String token = UUID.randomUUID().toString();

            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .userId(user.getId())
                    .token(token)
                    .expiresAt(LocalDateTime.now().plusMinutes(RESET_TOKEN_EXPIRY_MINUTES))
                    .build();
            passwordResetTokenRepository.save(resetToken);

            auditService.log(user.getId(), "FORGOT_PASSWORD", "User", user.getId(),
                    "Password reset requested, token generated");
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        validatePassword(request.getNewPassword());

        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new BadRequestException("Invalid password reset token"));

        if (resetToken.getUsedAt() != null) {
            throw new BadRequestException("Password reset token has already been used");
        }

        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Password reset token has expired");
        }

        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        resetToken.setUsedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(resetToken);

        refreshTokenService.revokeAllUserTokens(user.getId());

        auditService.log(user.getId(), "RESET_PASSWORD", "User", user.getId(),
                "Password reset successfully, all sessions revoked");
    }

    @Transactional
    public void logout(RefreshRequest request) {
        String token = request.getRefreshToken();
        Long userId = null;

        if (jwtTokenProvider.validateToken(token)) {
            userId = jwtTokenProvider.getUserIdFromToken(token);
        }

        refreshTokenService.revokeToken(token);

        if (userId != null) {
            auditService.log(userId, "LOGOUT", "User", userId, "User logged out, refresh token revoked");
        }
    }

    private void validatePassword(String password) {
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            throw new BadRequestException(
                    "Password must be at least 10 characters and contain at least one letter, one digit, and one special character");
        }
    }
}
