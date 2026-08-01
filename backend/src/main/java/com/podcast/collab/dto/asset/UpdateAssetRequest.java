package com.podcast.collab.dto.asset;

import lombok.Data;

@Data
public class UpdateAssetRequest {

    private String name;
    private String category;
    private String fileUrl;
    private String content;
}
