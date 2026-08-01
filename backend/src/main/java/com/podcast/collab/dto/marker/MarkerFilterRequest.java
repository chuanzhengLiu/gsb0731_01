package com.podcast.collab.dto.marker;

import com.podcast.collab.entity.enums.MarkerStatus;
import com.podcast.collab.entity.enums.MarkerType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarkerFilterRequest {

    private MarkerType type;

    private MarkerStatus status;

    private Long createdBy;

    private String keyword;
}
