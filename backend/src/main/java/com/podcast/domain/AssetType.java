package com.podcast.domain;

/**
 * Asset kind (README §4.5 素材库). Audio assets (开场音乐/过渡音效/广告片花)
 * carry a file; text assets (口播文案/赞助商口播/slogan) carry text content.
 */
public enum AssetType {
    AUDIO,
    TEXT
}
