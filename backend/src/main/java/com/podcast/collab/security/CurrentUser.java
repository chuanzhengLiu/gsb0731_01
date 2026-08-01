package com.podcast.collab.security;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 当前登录用户主体 */
@Data
@AllArgsConstructor
public class CurrentUser {
    private Long userId;
    private Long teamId;
    private String role;
}
