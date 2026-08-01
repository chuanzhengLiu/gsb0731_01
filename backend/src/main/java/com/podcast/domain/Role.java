package com.podcast.domain;

/**
 * Global/team roles as defined in README §3.1.
 * ADMIN=团队管理员, PRODUCER=制作人/主编, EDITOR=剪辑师,
 * OPERATOR=运营, HOST=主播/嘉宾, GUEST=访客(外部合作).
 */
public enum Role {
    ADMIN,
    PRODUCER,
    EDITOR,
    OPERATOR,
    HOST,
    GUEST
}
