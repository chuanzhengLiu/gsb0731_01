package com.podcast.domain;

/** Timeline marker types (README §4.2). */
public enum MarkerType {
    MISSPEAK,        // 口误（需删除）
    RE_RECORD,       // 补录（需替换）
    VOLUME_ISSUE,    // 音量问题
    BACKGROUND_MUSIC,// 背景音乐
    SOUND_EFFECT,    // 音效插入
    BAD_TRANSITION,  // 过渡不自然
    FACT_CHECK       // 事实待核实
}
