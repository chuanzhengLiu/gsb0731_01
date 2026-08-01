package com.podcast.collab.entity;

/** 分发状态：未开始→已提交→审核中→已上线→被拒绝 */
public enum DistributionStatus {
    NOT_STARTED, SUBMITTED, UNDER_REVIEW, LIVE, REJECTED
}
