package com.podcast.collab.dto.distribution;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformResponse {

    private Long id;
    private String name;
    private String displayName;
    private String rssRequiredFieldsJson;
    private String categoryOptionsJson;
    private Boolean isActive;
    private LocalDateTime createdAt;
}
