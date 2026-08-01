package com.podcast.domain;

/**
 * Episode lifecycle (README §4.1):
 * 策划→录制→粗剪→精剪→审听→定稿→分发→已发布
 */
public enum EpisodeStatus {
    PLANNING,      // 策划
    RECORDING,     // 录制
    ROUGH_CUT,     // 粗剪
    FINE_CUT,      // 精剪
    REVIEW,        // 审听
    FINALIZED,     // 定稿
    DISTRIBUTING,  // 分发
    PUBLISHED      // 已发布
}
