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
public class ShareAccessLogResponse {

    private Long id;
    private Long shareLinkId;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime accessedAt;
}
