package com.podcast.collab.controller;

import com.podcast.collab.dto.Dtos.*;
import com.podcast.collab.entity.*;
import com.podcast.collab.repository.*;
import com.podcast.collab.security.SecurityUtils;
import com.podcast.collab.service.AuditService;
import com.podcast.collab.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuthService authService;
    private final AuditService auditService;
    private final com.podcast.collab.service.MailService mailService;

    @org.springframework.beans.factory.annotation.Value("${app.public-base-url}")
    private String publicBaseUrl;

    @GetMapping("/current")
    public TeamInfo current() {
        Team team = teamRepository.findById(SecurityUtils.currentTeamId())
                .orElseThrow(() -> new IllegalArgumentException("团队不存在"));
        return new TeamInfo(team.getId(), team.getName(), team.getCreatedAt());
    }

    @PutMapping("/current")
    public TeamInfo update(@Valid @RequestBody UpdateTeamRequest req) {
        SecurityUtils.requireRole("ADMIN");
        Team team = teamRepository.findById(SecurityUtils.currentTeamId())
                .orElseThrow(() -> new IllegalArgumentException("团队不存在"));
        team.setName(req.name());
        teamRepository.save(team);
        return new TeamInfo(team.getId(), team.getName(), team.getCreatedAt());
    }

    @GetMapping("/members")
    public List<MemberInfo> members() {
        Long teamId = SecurityUtils.currentTeamId();
        return teamMemberRepository.findByTeamId(teamId).stream().map(m -> {
            User u = userRepository.findById(m.getUserId()).orElse(null);
            return new MemberInfo(m.getId(), m.getUserId(),
                    u != null ? u.getName() : "未知", u != null ? u.getEmail() : "",
                    m.getRoleInTeam().name(), m.getJoinedAt());
        }).toList();
    }

    /** 管理员通过邮箱邀请成员 */
    @PostMapping("/invites")
    public Map<String, String> invite(@Valid @RequestBody InviteRequest req) {
        SecurityUtils.requireRole("ADMIN");
        Long teamId = SecurityUtils.currentTeamId();
        Role role;
        try {
            role = Role.valueOf(req.role());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("角色非法");
        }
        if (userRepository.findByEmail(req.email()).isPresent()) {
            throw new IllegalArgumentException("该邮箱已注册");
        }
        String token = authService.createInvite(teamId, req.email(), role);
        auditService.log(SecurityUtils.currentUserId(), teamId, "INVITE_CREATE", "invite", null,
                "邀请 " + req.email() + " 角色 " + role);
        // 邀请链接只经邮件通道下发给被邀请人，不在响应中返回 token
        String inviteLink = publicBaseUrl + "/invite?token=" + token;
        mailService.send(req.email(), "播客协作系统团队邀请（24小时内有效）",
                "你被邀请加入团队，请点击链接完成注册（24小时内有效）：\n" + inviteLink);
        return Map.of("message", "邀请链接已通过邮件发送（24小时内有效）");
    }

    @DeleteMapping("/members/{id}")
    public Map<String, String> removeMember(@PathVariable Long id) {
        SecurityUtils.requireRole("ADMIN");
        Long teamId = SecurityUtils.currentTeamId();
        TeamMember member = teamMemberRepository.findById(id)
                .filter(m -> m.getTeamId().equals(teamId))
                .orElseThrow(() -> new IllegalArgumentException("成员不存在"));
        if (member.getUserId().equals(SecurityUtils.currentUserId())) {
            throw new IllegalArgumentException("不能移除自己");
        }
        teamMemberRepository.delete(member);
        // 同步清除用户的团队归属
        userRepository.findById(member.getUserId()).ifPresent(u -> {
            u.setTeamId(null);
            userRepository.save(u);
        });
        auditService.log(SecurityUtils.currentUserId(), teamId, "MEMBER_REMOVE", "user", member.getUserId(), "移除成员");
        return Map.of("message", "成员已移除");
    }

    /** 操作审计日志 */
    @GetMapping("/audit-logs")
    public List<AuditLog> auditLogs() {
        SecurityUtils.requireRole("ADMIN");
        return auditLogRepository.findByTeamIdOrderByCreatedAtDesc(SecurityUtils.currentTeamId());
    }
}
