package com.podcast.collab.dto.asset;

import com.podcast.collab.entity.enums.AssetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateAssetRequest {

    @NotBlank(message = "Asset name is required")
    private String name;

    @NotNull(message = "Asset type is required")
    private AssetType type;

    private String category;

    private String fileUrl;

    private String content;
}
