package com.podcast.collab.dto.share;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareLinkResponse {

    private Long id;
    private Long episodeId;
    private String token;
    private Long createdBy;
    private LocalDateTime expiresAt;
    private LocalDateTime lastAccessedAt;
    private Integer accessCount;
    private Boolean isRevoked;
    private LocalDateTime createdAt;
    private String episodeTitle;
}
