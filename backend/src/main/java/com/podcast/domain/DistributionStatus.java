package com.podcast.domain;

/**
 * Per-platform distribution status (README §4.4 分发状态追踪):
 * 未开始→已提交→审核中→已上线→被拒绝. Each platform tracked independently.
 */
public enum DistributionStatus {
    NOT_STARTED,   // 未开始
    SUBMITTED,     // 已提交
    IN_REVIEW,     // 审核中
    PUBLISHED,     // 已上线
    REJECTED       // 被拒绝
}
