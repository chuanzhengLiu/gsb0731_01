package com.podcast.collab.entity;

/** 单集状态：策划→录制→粗剪→精剪→审听→定稿→分发→已发布 */
public enum EpisodeStatus {
    PLANNING, RECORDED, ROUGH_CUT, FINE_CUT, REVIEW, FINALIZED, DISTRIBUTING, PUBLISHED
}
