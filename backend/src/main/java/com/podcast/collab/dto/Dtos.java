package com.podcast.collab.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 统一 DTO 定义 */
public final class Dtos {
    private Dtos() {
    }

    // ============ 认证 ============
    public record RegisterRequest(
            @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不正确") String email,
            @NotBlank(message = "密码不能为空") String password,
            @NotBlank(message = "姓名不能为空") String name,
            @NotBlank(message = "团队名称不能为空") String teamName) {
    }

    public record LoginRequest(
            @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不正确") String email,
            @NotBlank(message = "密码不能为空") String password) {
    }

    public record RefreshRequest(@NotBlank(message = "refresh_token 不能为空") String refreshToken) {
    }

    public record ForgotPasswordRequest(
            @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不正确") String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank(message = "token 不能为空") String token,
            @NotBlank(message = "新密码不能为空") String newPassword) {
    }

    public record AcceptInviteRequest(
            @NotBlank(message = "token 不能为空") String token,
            @NotBlank(message = "姓名不能为空") String name,
            @NotBlank(message = "密码不能为空") String password) {
    }

    public record AuthResponse(String accessToken, String refreshToken, UserInfo user) {
    }

    public record UserInfo(Long id, String email, String name, String role, Long teamId, String teamName) {
    }

    public record SessionInfo(Long id, String ipAddress, String userAgent,
                              LocalDateTime createdAt, LocalDateTime expiresAt, boolean current) {
    }

    // ============ 团队 ============
    public record InviteRequest(
            @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不正确") String email,
            @NotBlank(message = "角色不能为空") String role) {
    }

    public record MemberInfo(Long id, Long userId, String name, String email,
                             String roleInTeam, LocalDateTime joinedAt) {
    }

    public record TeamInfo(Long id, String name, LocalDateTime createdAt) {
    }

    public record UpdateTeamRequest(@NotBlank(message = "团队名称不能为空") String name) {
    }

    // ============ 节目 ============
    public record PodcastRequest(
            @NotBlank(message = "节目名称不能为空") String name,
            @NotBlank(message = "节目类型不能为空") @Pattern(regexp = "INTERVIEW|NARRATIVE|KNOWLEDGE|NEWS",
                    message = "类型必须是 INTERVIEW/NARRATIVE/KNOWLEDGE/NEWS") String type,
            String updateFrequency,
            @Min(value = 1, message = "目标时长必须大于0") Integer targetDuration,
            List<StructureSection> structureTemplate) {
    }

    /** 节目结构模板板块 */
    public record StructureSection(@NotBlank String name, @Min(1) int durationSec) {
    }

    // ============ 单集 ============
    public record EpisodeRequest(
            @NotNull(message = "集数不能为空") @Min(value = 1, message = "集数必须大于0") Integer number,
            @NotBlank(message = "标题不能为空") String title,
            String theme,
            LocalDate recordDate,
            LocalDateTime scheduledPublishAt) {
    }

    public record EpisodeStatusRequest(@NotBlank(message = "状态不能为空") String status) {
    }

    // ============ 任务 ============
    public record TaskRequest(
            @NotBlank(message = "任务描述不能为空") @Size(max = 1024) String description,
            Long assigneeId,
            LocalDate dueDate) {
    }

    public record TaskStatusRequest(@NotBlank(message = "状态不能为空")
                                    @Pattern(regexp = "TODO|IN_PROGRESS|DONE", message = "状态非法") String status) {
    }

    // ============ 标记 ============
    public record MarkerRequest(
            @NotNull(message = "起始时间不能为空") @Min(0) Long startTimeMs,
            @Min(0) Long endTimeMs,
            @NotBlank(message = "标记类型不能为空") String type,
            @Size(max = 5000) String description,
            String screenshotUrl) {
    }

    public record MarkerStatusRequest(@NotBlank(message = "状态不能为空") String status) {
    }

    // ============ 转写 ============
    public record TranscriptSegmentRequest(
            @NotNull @Min(0) Long startTimeMs,
            @NotNull @Min(0) Long endTimeMs,
            @NotBlank(message = "文本不能为空") String text,
            String speaker) {
    }

    public record TranscriptImportRequest(@NotEmpty(message = "转写片段不能为空")
                                          List<TranscriptSegmentRequest> segments) {
    }

    // ============ 分发 ============
    public record PlatformRequest(
            @NotBlank(message = "平台名称不能为空") String name,
            String accountName,
            String rssRequiredFieldsJson,
            String categoryOptionsJson) {
    }

    public record DistributionRequest(
            @NotNull(message = "平台不能为空") Long platformId,
            String platformDataJson,
            LocalDateTime scheduledAt) {
    }

    public record DistributionStatusRequest(@NotBlank(message = "状态不能为空") String status) {
    }

    // ============ 素材 ============
    public record AssetRequest(
            @NotBlank(message = "素材名称不能为空") String name,
            @NotBlank(message = "素材类型不能为空") @Pattern(regexp = "AUDIO|TEXT", message = "类型必须是 AUDIO/TEXT") String type,
            String category,
            String content) {
    }

    public record AssetUsageRequest(
            @NotNull(message = "单集不能为空") Long episodeId,
            @Min(0) Long positionMs) {
    }

    // ============ 分享 ============
    public record ShareLinkResponse(String token, String url, LocalDateTime expiresAt) {
    }
}
