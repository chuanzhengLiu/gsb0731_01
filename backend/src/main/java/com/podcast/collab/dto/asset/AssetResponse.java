package com.podcast.collab.dto.asset;

import com.podcast.collab.entity.enums.AssetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetResponse {

    private Long id;
    private Long teamId;
    private String name;
    private AssetType type;
    private String category;
    private String fileUrl;
    private String content;
    private Integer usageCount;
    private Long createdBy;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
